package com.emptycastle.novery.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import com.emptycastle.novery.data.backup.quicknovel.QuickNovelBackupConverter
import com.emptycastle.novery.data.local.NovelDatabase
import com.emptycastle.novery.data.local.PreferencesManager
import com.emptycastle.novery.data.local.entity.BookmarkEntity
import com.emptycastle.novery.data.local.entity.HistoryEntity
import com.emptycastle.novery.data.local.entity.LibraryEntity
import com.emptycastle.novery.data.local.entity.ReadChapterEntity
import com.emptycastle.novery.data.local.entity.ReadingStatsEntity
import com.emptycastle.novery.data.local.entity.ReadingStreakEntity
import com.emptycastle.novery.domain.model.AppSettings
import com.emptycastle.novery.domain.model.DisplayMode
import com.emptycastle.novery.domain.model.FontFamily
import com.emptycastle.novery.domain.model.FontWeight
import com.emptycastle.novery.domain.model.GridColumns
import com.emptycastle.novery.domain.model.LibraryFilter
import com.emptycastle.novery.domain.model.LibrarySortOrder
import com.emptycastle.novery.domain.model.MaxWidth
import com.emptycastle.novery.domain.model.PageAnimation
import com.emptycastle.novery.domain.model.ProgressStyle
import com.emptycastle.novery.domain.model.RatingFormat
import com.emptycastle.novery.domain.model.ReaderSettings
import com.emptycastle.novery.domain.model.ReaderTheme
import com.emptycastle.novery.domain.model.ReadingDirection
import com.emptycastle.novery.domain.model.ReadingStatus
import com.emptycastle.novery.domain.model.ScrollMode
import com.emptycastle.novery.domain.model.TapAction
import com.emptycastle.novery.domain.model.TapZoneConfig
import com.emptycastle.novery.domain.model.TextAlign
import com.emptycastle.novery.domain.model.ThemeMode
import com.emptycastle.novery.domain.model.UiDensity
import com.emptycastle.novery.domain.model.VolumeKeyDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    private val context: Context,
    private val database: NovelDatabase,
    private val preferencesManager: PreferencesManager
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // QuickNovel backup converter
    private val quickNovelConverter = QuickNovelBackupConverter()

    // ================================================================
    // CREATE BACKUP
    // ================================================================

    /**
     * Create a complete backup of all app data
     */
    suspend fun createBackup(): BackupData = withContext(Dispatchers.IO) {
        val libraryDao = database.libraryDao()
        val bookmarkDao = database.bookmarkDao()
        val historyDao = database.historyDao()
        val statsDao = database.statsDao()
        val workDao = database.workDao()

        BackupData(
            version = BackupData.CURRENT_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = getAppVersion(),
            deviceInfo = getDeviceInfo(),
            library = libraryDao.getAll().map { it.toBackup() },
            bookmarks = bookmarkDao.getAll().map { it.toBackup() },
            history = historyDao.getAll().map { it.toBackup() },
            readChapters = historyDao.getAllReadChapters().map { it.toBackup() },
            readingStats = statsDao.getAllStats().map { it.toBackup() },
            readingStreak = statsDao.getStreak()?.toBackup(),
            appSettings = preferencesManager.appSettings.value.toBackup(),
            readerSettings = preferencesManager.readerSettings.value.toBackup(),
            // Slice-06.1: work identities + custom filter rules.
            works = buildWorksBackup(workDao),
            textFilters = buildTextFiltersBackup()
        )
    }

    private suspend fun buildWorksBackup(
        workDao: com.emptycastle.novery.data.local.dao.WorkDao
    ): List<WorkBackup> {
        return try {
            val projections = workDao.getAllProjections()
            projections.groupBy { it.workId }.mapNotNull { (workId, rows) ->
                if (rows.isEmpty()) return@mapNotNull null
                val work = try {
                    workDao.getWork(workId)
                } catch (_: Exception) {
                    null
                }
                WorkBackup(
                    defaultNovelUrl = work?.defaultNovelUrl
                        ?: rows.firstOrNull()?.novelUrl,
                    projections = rows.map {
                        WorkProjectionBackup(
                            novelUrl = it.novelUrl,
                            providerName = it.providerName
                        )
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "works backup failed", e)
            emptyList()
        }
    }

    private fun buildTextFiltersBackup(): List<TextFilterBackup> {
        return try {
            com.emptycastle.novery.util.TextFilterManager(preferencesManager)
                .getFilters()
                .filter { !it.isBuiltin }
                .map {
                    TextFilterBackup(
                        pattern = it.pattern,
                        isRegex = it.isRegex,
                        label = it.label,
                        isEnabled = it.isEnabled
                    )
                }
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "text filter backup failed", e)
            emptyList()
        }
    }

    /**
     * Export backup to JSON string (v2 checksummed envelope).
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val backup = createBackup()
        json.encodeToString(BackupIntegrity.wrap(backup))
    }

    /**
     * Export backup to a URI (file)
     */
    suspend fun exportToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupJson = exportToJson()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(backupJson.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate suggested backup filename
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault())
        val date = dateFormat.format(Date())
        return "novery_backup_$date.${BackupData.FILE_EXTENSION}"
    }

    // ================================================================
    // RESTORE BACKUP
    // ================================================================

    /**
     * Parse backup metadata without fully restoring
     * Supports both Novery and QuickNovel formats
     */
    suspend fun parseBackupMetadata(uri: Uri): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            val backupJson = readFromUri(uri)
                ?: return@withContext Result.failure(Exception("Could not read backup file"))

            // Check if it's a QuickNovel backup
            if (quickNovelConverter.isQuickNovelBackup(backupJson)) {
                val converted = quickNovelConverter.convert(backupJson)
                return@withContext Result.success(BackupMetadata(
                    version = converted.version,
                    createdAt = converted.createdAt,
                    appVersion = "QuickNovel Import",
                    deviceInfo = converted.deviceInfo,
                    libraryCount = converted.library.size,
                    bookmarkCount = converted.bookmarks.size,
                    historyCount = converted.history.size,
                    readChaptersCount = converted.readChapters.size,
                    hasSettings = converted.appSettings != null,
                    hasStatistics = false,
                    sourceApp = "QuickNovel"
                ))
            }

            // Regular Novery backup: v2 envelope (verified) or v1 bare (legacy).
            when (val unwrapped = BackupIntegrity.unwrap(backupJson)) {
                is BackupIntegrity.Unwrapped.Corrupt -> {
                    return@withContext Result.failure(Exception(unwrapped.reason))
                }
                is BackupIntegrity.Unwrapped.Valid -> {
                    val backup = unwrapped.backup
                    Result.success(BackupMetadata(
                        version = backup.version,
                        createdAt = backup.createdAt,
                        appVersion = backup.appVersion,
                        deviceInfo = backup.deviceInfo,
                        libraryCount = backup.library.size,
                        bookmarkCount = backup.bookmarks.size,
                        historyCount = backup.history.size,
                        readChaptersCount = backup.readChapters.size,
                        hasSettings = backup.appSettings != null,
                        hasStatistics = backup.readingStats.isNotEmpty() || backup.readingStreak != null,
                        sourceApp = "Novery",
                        worksCount = backup.works.size,
                        textFiltersCount = backup.textFilters.size,
                        checksumValid = true
                    ))
                }
                is BackupIntegrity.Unwrapped.Legacy -> {
                    val backup = unwrapped.backup
                    Result.success(BackupMetadata(
                        version = backup.version,
                        createdAt = backup.createdAt,
                        appVersion = backup.appVersion,
                        deviceInfo = backup.deviceInfo,
                        libraryCount = backup.library.size,
                        bookmarkCount = backup.bookmarks.size,
                        historyCount = backup.history.size,
                        readChaptersCount = backup.readChapters.size,
                        hasSettings = backup.appSettings != null,
                        hasStatistics = backup.readingStats.isNotEmpty() || backup.readingStreak != null,
                        sourceApp = "Novery",
                        worksCount = backup.works.size,
                        textFiltersCount = backup.textFilters.size,
                        checksumValid = null
                    ))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restore backup from URI with options
     * Supports both Novery and QuickNovel formats
     */
    suspend fun restoreFromUri(
        uri: Uri,
        options: RestoreOptions = RestoreOptions()
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val backupJson = readFromUri(uri)
                ?: return@withContext RestoreResult(
                    success = false,
                    error = "Could not read backup file"
                )

            val backup = try {
                // Check if it's a QuickNovel backup and convert it
                if (quickNovelConverter.isQuickNovelBackup(backupJson)) {
                    quickNovelConverter.convert(backupJson)
                } else {
                    when (val unwrapped = BackupIntegrity.unwrap(backupJson)) {
                        is BackupIntegrity.Unwrapped.Corrupt -> {
                            return@withContext RestoreResult(
                                success = false,
                                error = unwrapped.reason
                            )
                        }
                        is BackupIntegrity.Unwrapped.Valid -> unwrapped.backup
                        is BackupIntegrity.Unwrapped.Legacy -> unwrapped.backup
                    }
                }
            } catch (e: Exception) {
                return@withContext RestoreResult(
                    success = false,
                    error = "Invalid backup format: ${e.message}"
                )
            }

            // Validate version (only for native Novery backups)
            if (backup.appVersion != "QuickNovel Import" &&
                backup.version > BackupData.CURRENT_VERSION) {
                return@withContext RestoreResult(
                    success = false,
                    error = "Backup version ${backup.version} is newer than supported version ${BackupData.CURRENT_VERSION}"
                )
            }

            restoreBackup(backup, options)
        } catch (e: Exception) {
            RestoreResult(
                success = false,
                error = e.message ?: "Unknown error during restore"
            )
        }
    }

    /**
     * Restore backup from JSON string
     */
    suspend fun restoreFromJson(
        backupJson: String,
        options: RestoreOptions = RestoreOptions()
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val backup = if (quickNovelConverter.isQuickNovelBackup(backupJson)) {
                quickNovelConverter.convert(backupJson)
            } else {
                when (val unwrapped = BackupIntegrity.unwrap(backupJson)) {
                    is BackupIntegrity.Unwrapped.Corrupt -> {
                        return@withContext RestoreResult(
                            success = false,
                            error = unwrapped.reason
                        )
                    }
                    is BackupIntegrity.Unwrapped.Valid -> unwrapped.backup
                    is BackupIntegrity.Unwrapped.Legacy -> unwrapped.backup
                }
            }
            restoreBackup(backup, options)
        } catch (e: Exception) {
            RestoreResult(
                success = false,
                error = e.message ?: "Unknown error parsing backup"
            )
        }
    }

    private suspend fun restoreBackup(
        backup: BackupData,
        options: RestoreOptions
    ): RestoreResult {
        val libraryDao = database.libraryDao()
        val bookmarkDao = database.bookmarkDao()
        val historyDao = database.historyDao()
        val statsDao = database.statsDao()

        var libraryCount = 0
        var bookmarkCount = 0
        var historyCount = 0
        var readChaptersCount = 0
        var statsCount = 0
        var settingsRestored = false
        var worksRestored = 0
        var textFiltersRestored = 0

        return try {
            // Clear existing data if not merging
            if (!options.mergeWithExisting) {
                if (options.restoreLibrary) libraryDao.deleteAll()
                if (options.restoreBookmarks) bookmarkDao.deleteAll()
                if (options.restoreHistory) {
                    historyDao.deleteAll()
                    // Clear all read chapters - need to iterate through novels
                    libraryDao.getAll().forEach {
                        historyDao.clearReadChapters(it.url)
                    }
                }
                if (options.restoreStatistics) {
                    statsDao.deleteAllStats()
                    statsDao.deleteStreak()
                }
            }

            // Restore library
            if (options.restoreLibrary) {
                backup.library.forEach { item ->
                    val entity = item.toEntity()
                    if (options.mergeWithExisting) {
                        val existing = libraryDao.getByUrl(entity.url)
                        if (existing == null) {
                            libraryDao.insert(entity)
                            libraryCount++
                        } else {
                            // Optionally merge: keep newer data
                            if ((entity.lastReadAt ?: 0) > (existing.lastReadAt ?: 0)) {
                                libraryDao.insert(entity)
                                libraryCount++
                            }
                        }
                    } else {
                        libraryDao.insert(entity)
                        libraryCount++
                    }
                }
            }

            // Restore bookmarks
            if (options.restoreBookmarks) {
                backup.bookmarks.forEach { item ->
                    bookmarkDao.insert(item.toEntity())
                    bookmarkCount++
                }
            }

            // Restore history
            if (options.restoreHistory) {
                backup.history.forEach { item ->
                    val existing = historyDao.getByNovelUrl(item.novelUrl)
                    if (existing == null || item.timestamp > existing.timestamp) {
                        historyDao.insert(item.toEntity())
                        historyCount++
                    }
                }

                // Restore read chapters
                backup.readChapters.forEach { item ->
                    historyDao.markChapterRead(item.toEntity())
                    readChaptersCount++
                }
            }

            // Restore statistics
            if (options.restoreStatistics) {
                backup.readingStats.forEach { item ->
                    statsDao.insertStats(item.toEntity())
                    statsCount++
                }
                backup.readingStreak?.let { streak ->
                    val existing = statsDao.getStreak()
                    // Keep the better streak
                    if (existing == null || streak.longestStreak > existing.longestStreak) {
                        statsDao.updateStreak(streak.toEntity())
                    }
                }
            }

            // Restore settings
            if (options.restoreSettings) {
                backup.appSettings?.let { settings ->
                    preferencesManager.updateAppSettings(settings.toAppSettings())
                }
                backup.readerSettings?.let { settings ->
                    preferencesManager.updateReaderSettings(settings.toReaderSettings())
                }
                settingsRestored = true
            }

            // Slice-06.1: restore work identities. Merge-aware: works whose
            // urls are all already attached are skipped; missing urls join
            // the first already-attached work instead of duplicating.
            if (options.restoreWorks && backup.works.isNotEmpty()) {
                val workDao = database.workDao()
                if (!options.mergeWithExisting) {
                    workDao.deleteAllProjections()
                    workDao.deleteAllWorks()
                }
                for (workBackup in backup.works) {
                    if (workBackup.projections.isEmpty()) continue
                    val attachedWorkId = workBackup.projections
                        .mapNotNull { workDao.getProjectionByUrl(it.novelUrl)?.workId }
                        .firstOrNull()
                    if (attachedWorkId != null && options.mergeWithExisting) {
                        var joined = false
                        for (projection in workBackup.projections) {
                            if (workDao.getProjectionByUrl(projection.novelUrl) == null) {
                                workDao.insertProjection(
                                    com.emptycastle.novery.data.local.entity.WorkProjectionEntity(
                                        workId = attachedWorkId,
                                        novelUrl = projection.novelUrl,
                                        providerName = projection.providerName
                                    )
                                )
                                joined = true
                            }
                        }
                        if (joined) worksRestored++
                        continue
                    }
                    val newId = workDao.insertWork(
                        com.emptycastle.novery.data.local.entity.WorkEntity(
                            defaultNovelUrl = workBackup.defaultNovelUrl
                        )
                    )
                    for (projection in workBackup.projections) {
                        if (workDao.getProjectionByUrl(projection.novelUrl) == null) {
                            workDao.insertProjection(
                                com.emptycastle.novery.data.local.entity.WorkProjectionEntity(
                                    workId = newId,
                                    novelUrl = projection.novelUrl,
                                    providerName = projection.providerName
                                )
                            )
                        }
                    }
                    worksRestored++
                }
            }

            // Slice-06.1: restore custom text-filter rules, skipping exact
            // duplicates already present.
            if (options.restoreTextFilters && backup.textFilters.isNotEmpty()) {
                val filterManager =
                    com.emptycastle.novery.util.TextFilterManager(preferencesManager)
                if (!options.mergeWithExisting) {
                    filterManager.getFilters()
                        .filter { !it.isBuiltin }
                        .forEach { filterManager.removeRule(it.id) }
                }
                val existing = filterManager.getFilters()
                    .map { it.pattern to it.isRegex }
                    .toMutableSet()
                for (rule in backup.textFilters) {
                    if (rule.pattern.isBlank()) continue
                    if ((rule.pattern to rule.isRegex) in existing) continue
                    val created = try {
                        filterManager.addCustomRule(rule.pattern, rule.isRegex, rule.label)
                    } catch (_: Exception) {
                        null
                    }
                    if (created != null) {
                        existing.add(rule.pattern to rule.isRegex)
                        if (!rule.isEnabled) {
                            filterManager.setEnabled(created.id, false)
                        }
                        textFiltersRestored++
                    }
                }
            }

            RestoreResult(
                success = true,
                libraryRestored = libraryCount,
                bookmarksRestored = bookmarkCount,
                historyRestored = historyCount,
                readChaptersRestored = readChaptersCount,
                statsRestored = statsCount,
                settingsRestored = settingsRestored,
                worksRestored = worksRestored,
                textFiltersRestored = textFiltersRestored
            )
        } catch (e: Exception) {
            RestoreResult(
                success = false,
                error = e.message ?: "Error during restore",
                libraryRestored = libraryCount,
                bookmarksRestored = bookmarkCount,
                historyRestored = historyCount,
                readChaptersRestored = readChaptersCount,
                statsRestored = statsCount,
                worksRestored = worksRestored,
                textFiltersRestored = textFiltersRestored
            )
        }
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private fun readFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "${packageInfo.versionName} ($versionCode)"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
}

// ================================================================
// EXTENSION FUNCTIONS - Entity to Backup
// ================================================================

private fun LibraryEntity.toBackup() = LibraryBackup(
    url = url,
    name = name,
    posterUrl = posterUrl,
    apiName = apiName,
    latestChapter = latestChapter,
    addedAt = addedAt,
    readingStatus = readingStatus,
    lastChapterUrl = lastChapterUrl,
    lastChapterName = lastChapterName,
    lastReadAt = lastReadAt,
    lastScrollIndex = lastScrollIndex,
    lastScrollOffset = lastScrollOffset,
    totalChapterCount = totalChapterCount,
    acknowledgedChapterCount = acknowledgedChapterCount,
    lastCheckedAt = lastCheckedAt,
    lastUpdatedAt = lastUpdatedAt,
    lastReadChapterIndex = lastReadChapterIndex,
    unreadChapterCount = unreadChapterCount
)

private fun BookmarkEntity.toBackup() = BookmarkBackup(
    novelUrl = novelUrl,
    novelName = novelName,
    chapterUrl = chapterUrl,
    chapterName = chapterName,
    segmentId = segmentId,
    segmentIndex = segmentIndex,
    textSnippet = textSnippet,
    note = note,
    category = category,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun HistoryEntity.toBackup() = HistoryBackup(
    novelUrl = novelUrl,
    novelName = novelName,
    posterUrl = posterUrl,
    chapterName = chapterName,
    chapterUrl = chapterUrl,
    apiName = apiName,
    timestamp = timestamp
)

private fun ReadChapterEntity.toBackup() = ReadChapterBackup(
    chapterUrl = chapterUrl,
    novelUrl = novelUrl,
    readAt = readAt
)

private fun ReadingStatsEntity.toBackup() = ReadingStatsBackup(
    novelUrl = novelUrl,
    novelName = novelName,
    date = date,
    readingTimeSeconds = readingTimeSeconds,
    chaptersRead = chaptersRead,
    wordsRead = wordsRead,
    sessionsCount = sessionsCount,
    longestSessionSeconds = longestSessionSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ReadingStreakEntity.toBackup() = ReadingStreakBackup(
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastReadDate = lastReadDate,
    totalDaysRead = totalDaysRead,
    totalReadingTimeSeconds = totalReadingTimeSeconds,
    updatedAt = updatedAt
)

private fun AppSettings.toBackup() = AppSettingsBackup(
    themeMode = themeMode.name,
    amoledBlack = amoledBlack,
    useDynamicColor = useDynamicColor,
    uiDensity = uiDensity.name,
    libraryGridColumns = GridColumns.toInt(libraryGridColumns),
    browseGridColumns = GridColumns.toInt(browseGridColumns),
    searchGridColumns = GridColumns.toInt(searchGridColumns),
    showBadges = showBadges,
    libraryDisplayMode = libraryDisplayMode.name,
    browseDisplayMode = browseDisplayMode.name,
    searchDisplayMode = searchDisplayMode.name,
    ratingFormat = ratingFormat.name,
    defaultLibrarySort = defaultLibrarySort.name,
    defaultLibraryFilter = LibraryFilter.sanitizeDefault(
        defaultLibraryFilter,
        enabledLibraryFilters
    ).name,
    hideSpicyLibraryContent = hideSpicyLibraryContent,
    enabledLibraryFilters = LibraryFilter.shelfOptions()
        .filter { it in enabledLibraryFilters }
        .map { it.name },
    autoDownloadEnabled = autoDownloadEnabled,
    autoDownloadOnWifiOnly = autoDownloadOnWifiOnly,
    autoDownloadLimit = autoDownloadLimit,
    autoDownloadForStatuses = autoDownloadForStatuses.map { it.name },
    searchResultsPerProvider = searchResultsPerProvider,
    keepScreenOn = keepScreenOn,
    infiniteScroll = infiniteScroll,
    providerOrder = providerOrder,
    disabledProviders = disabledProviders.toList()
)

private fun ReaderSettings.toBackup() = ReaderSettingsBackup(
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontFamily = fontFamily.id,
    fontWeight = fontWeight.value,
    textAlign = textAlign.id,
    theme = theme.id,
    marginHorizontal = marginHorizontal,
    marginVertical = marginVertical,
    paragraphSpacing = paragraphSpacing,
    paragraphIndent = paragraphIndent,
    letterSpacing = letterSpacing,
    wordSpacing = wordSpacing,
    maxWidth = maxWidth.id,
    hyphenation = hyphenation,
    keepScreenOn = keepScreenOn,
    showProgress = showProgress,
    progressStyle = progressStyle.id,
    showReadingTime = showReadingTime,
    showChapterTitle = showChapterTitle,
    volumeKeyNavigation = volumeKeyNavigation,
    volumeKeyDirection = volumeKeyDirection.id,
    readingDirection = readingDirection.id,
    scrollMode = scrollMode.id,
    pageAnimation = pageAnimation.id,
    smoothScroll = smoothScroll,
    scrollSensitivity = scrollSensitivity,
    edgeGestures = edgeGestures,
    longPressSelection = longPressSelection,
    autoHideControlsDelay = autoHideControlsDelay,
    brightness = brightness,
    warmthFilter = warmthFilter,
    autoScrollEnabled = autoScrollEnabled,
    autoScrollSpeed = autoScrollSpeed,
    forceHighContrast = forceHighContrast,
    reduceMotion = reduceMotion,
    largerTouchTargets = largerTouchTargets,
    // Tap zones
    tapHorizontalZoneRatio = tapZones.horizontalZoneRatio,
    tapVerticalZoneRatio = tapZones.verticalZoneRatio,
    tapLeftAction = tapZones.leftZoneAction.id,
    tapRightAction = tapZones.rightZoneAction.id,
    tapTopAction = tapZones.topZoneAction.id,
    tapBottomAction = tapZones.bottomZoneAction.id,
    tapCenterAction = tapZones.centerZoneAction.id,
    tapDoubleTapAction = tapZones.doubleTapAction.id
)

// ================================================================
// EXTENSION FUNCTIONS - Backup to Entity
// ================================================================

private fun LibraryBackup.toEntity() = LibraryEntity(
    url = url,
    name = name,
    posterUrl = posterUrl,
    apiName = apiName,
    latestChapter = latestChapter,
    addedAt = addedAt,
    readingStatus = readingStatus,
    lastChapterUrl = lastChapterUrl,
    lastChapterName = lastChapterName,
    lastReadAt = lastReadAt,
    lastScrollIndex = lastScrollIndex,
    lastScrollOffset = lastScrollOffset,
    totalChapterCount = totalChapterCount,
    acknowledgedChapterCount = acknowledgedChapterCount,
    lastCheckedAt = lastCheckedAt,
    lastUpdatedAt = lastUpdatedAt,
    lastReadChapterIndex = lastReadChapterIndex,
    unreadChapterCount = unreadChapterCount
)

private fun BookmarkBackup.toEntity() = BookmarkEntity(
    novelUrl = novelUrl,
    novelName = novelName,
    chapterUrl = chapterUrl,
    chapterName = chapterName,
    segmentId = segmentId,
    segmentIndex = segmentIndex,
    textSnippet = textSnippet,
    note = note,
    category = category,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun HistoryBackup.toEntity() = HistoryEntity(
    novelUrl = novelUrl,
    novelName = novelName,
    posterUrl = posterUrl,
    chapterName = chapterName,
    chapterUrl = chapterUrl,
    apiName = apiName,
    timestamp = timestamp
)

private fun ReadChapterBackup.toEntity() = ReadChapterEntity(
    chapterUrl = chapterUrl,
    novelUrl = novelUrl,
    readAt = readAt
)

private fun ReadingStatsBackup.toEntity() = ReadingStatsEntity(
    novelUrl = novelUrl,
    novelName = novelName,
    date = date,
    readingTimeSeconds = readingTimeSeconds,
    chaptersRead = chaptersRead,
    wordsRead = wordsRead,
    sessionsCount = sessionsCount,
    longestSessionSeconds = longestSessionSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ReadingStreakBackup.toEntity() = ReadingStreakEntity(
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastReadDate = lastReadDate,
    totalDaysRead = totalDaysRead,
    totalReadingTimeSeconds = totalReadingTimeSeconds,
    updatedAt = updatedAt
)

private fun AppSettingsBackup.toAppSettings(): AppSettings {
    val restoredEnabledLibraryFilters = LibraryFilter.sanitizeEnabledShelves(
        enabledLibraryFilters.mapNotNull {
            try { LibraryFilter.valueOf(it) } catch (e: Exception) { null }
        }.toSet()
    )

    return AppSettings(
        themeMode = try { ThemeMode.valueOf(themeMode) } catch (e: Exception) { ThemeMode.DARK },
        amoledBlack = amoledBlack,
        useDynamicColor = useDynamicColor,
        uiDensity = try { UiDensity.valueOf(uiDensity) } catch (e: Exception) { UiDensity.DEFAULT },
        libraryGridColumns = GridColumns.fromInt(libraryGridColumns),
        browseGridColumns = GridColumns.fromInt(browseGridColumns),
        searchGridColumns = GridColumns.fromInt(searchGridColumns),
        showBadges = showBadges,
        libraryDisplayMode = try { DisplayMode.valueOf(libraryDisplayMode) } catch (e: Exception) { DisplayMode.GRID },
        browseDisplayMode = try { DisplayMode.valueOf(browseDisplayMode) } catch (e: Exception) { DisplayMode.GRID },
        searchDisplayMode = try { DisplayMode.valueOf(searchDisplayMode) } catch (e: Exception) { DisplayMode.GRID },
        ratingFormat = try { RatingFormat.valueOf(ratingFormat) } catch (e: Exception) { RatingFormat.TEN_POINT },
        defaultLibrarySort = try { LibrarySortOrder.valueOf(defaultLibrarySort) } catch (e: Exception) { LibrarySortOrder.LAST_READ },
        enabledLibraryFilters = restoredEnabledLibraryFilters,
        defaultLibraryFilter = try {
            LibraryFilter.sanitizeDefault(
                LibraryFilter.valueOf(defaultLibraryFilter),
                restoredEnabledLibraryFilters
            )
        } catch (e: Exception) {
            LibraryFilter.sanitizeDefault(
                LibraryFilter.DOWNLOADED,
                restoredEnabledLibraryFilters
            )
        },
        hideSpicyLibraryContent = hideSpicyLibraryContent,
        autoDownloadEnabled = autoDownloadEnabled,
        autoDownloadOnWifiOnly = autoDownloadOnWifiOnly,
        autoDownloadLimit = autoDownloadLimit,
        autoDownloadForStatuses = autoDownloadForStatuses.mapNotNull {
            try { ReadingStatus.valueOf(it) } catch (e: Exception) { null }
        }.toSet(),
        searchResultsPerProvider = searchResultsPerProvider,
        keepScreenOn = keepScreenOn,
        infiniteScroll = infiniteScroll,
        providerOrder = providerOrder,
        disabledProviders = disabledProviders.toSet()
    )
}

private fun ReaderSettingsBackup.toReaderSettings(): ReaderSettings {
    val fontFamily = FontFamily.fromId(fontFamily) ?: FontFamily.SYSTEM_SERIF
    val fontWeight = FontWeight.fromValue(fontWeight)
    val textAlign = TextAlign.fromId(textAlign)
    val theme = ReaderTheme.fromId(theme)
    val maxWidthEnum = MaxWidth.fromId(maxWidth)
    val progressStyleEnum = ProgressStyle.fromId(progressStyle)
    val volumeKeyDir = VolumeKeyDirection.fromId(volumeKeyDirection)
    val readingDir = ReadingDirection.fromId(readingDirection)
    val scrollModeEnum = ScrollMode.fromId(scrollMode)
    val pageAnimationEnum = PageAnimation.fromId(pageAnimation)

    val tapZoneConfig = TapZoneConfig(
        horizontalZoneRatio = tapHorizontalZoneRatio,
        verticalZoneRatio = tapVerticalZoneRatio,
        leftZoneAction = TapAction.fromId(tapLeftAction),
        rightZoneAction = TapAction.fromId(tapRightAction),
        topZoneAction = TapAction.fromId(tapTopAction),
        bottomZoneAction = TapAction.fromId(tapBottomAction),
        centerZoneAction = TapAction.fromId(tapCenterAction),
        doubleTapAction = TapAction.fromId(tapDoubleTapAction)
    )

    return ReaderSettings(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        textAlign = textAlign,
        theme = theme,
        marginHorizontal = marginHorizontal,
        marginVertical = marginVertical,
        paragraphSpacing = paragraphSpacing,
        paragraphIndent = paragraphIndent,
        letterSpacing = letterSpacing,
        wordSpacing = wordSpacing,
        maxWidth = maxWidthEnum,
        hyphenation = hyphenation,
        keepScreenOn = keepScreenOn,
        showProgress = showProgress,
        progressStyle = progressStyleEnum,
        showReadingTime = showReadingTime,
        showChapterTitle = showChapterTitle,
        volumeKeyNavigation = volumeKeyNavigation,
        volumeKeyDirection = volumeKeyDir,
        readingDirection = readingDir,
        scrollMode = scrollModeEnum,
        pageAnimation = pageAnimationEnum,
        smoothScroll = smoothScroll,
        scrollSensitivity = scrollSensitivity,
        edgeGestures = edgeGestures,
        longPressSelection = longPressSelection,
        autoHideControlsDelay = autoHideControlsDelay,
        brightness = brightness,
        warmthFilter = warmthFilter,
        autoScrollEnabled = autoScrollEnabled,
        autoScrollSpeed = autoScrollSpeed,
        forceHighContrast = forceHighContrast,
        reduceMotion = reduceMotion,
        largerTouchTargets = largerTouchTargets,
        tapZones = tapZoneConfig
    )
}
