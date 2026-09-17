package com.emptycastle.novery.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.emptycastle.novery.data.repository.LibraryItem
import com.emptycastle.novery.data.repository.RepositoryProvider
import com.emptycastle.novery.data.repository.WorkMigration
import com.emptycastle.novery.data.repository.WorkRepository
import com.emptycastle.novery.domain.model.Chapter
import com.emptycastle.novery.domain.model.NovelDetails
import com.emptycastle.novery.domain.model.Novel
import com.emptycastle.novery.domain.model.RatingFormat
import com.emptycastle.novery.domain.model.ReadingStatus
import com.emptycastle.novery.util.RatingUtils
import kotlinx.coroutines.launch

// ================================================================
// COLORS
// ================================================================

private object ActionSheetColors {
    val Pink = Color(0xFFE91E63)
    val Success = Color(0xFF22C55E)
    val Star = Color(0xFFFBBF24)
    val Error = Color(0xFFEF4444)

    val StatusReading = Color(0xFF3B82F6)
    val StatusSpicy = Color(0xFFF97316)
    val StatusCompleted = Color(0xFF22C55E)
    val StatusOnHold = Color(0xFFF59E0B)
    val StatusPlanToRead = Color(0xFF8B5CF6)
    val StatusDropped = Color(0xFFEF4444)

    fun forStatus(status: ReadingStatus) = when (status) {
        ReadingStatus.READING -> StatusReading
        ReadingStatus.SPICY -> StatusSpicy
        ReadingStatus.COMPLETED -> StatusCompleted
        ReadingStatus.ON_HOLD -> StatusOnHold
        ReadingStatus.PLAN_TO_READ -> StatusPlanToRead
        ReadingStatus.DROPPED -> StatusDropped
    }
}

private enum class StatusPickerMode {
    ADD,
    UPDATE
}

// ================================================================
// DATA CLASS
// ================================================================

data class NovelActionSheetData(
    val novel: Novel,
    val synopsis: String? = null,
    val isInLibrary: Boolean = false,
    val lastChapterName: String? = null,
    val providerName: String? = null,
    val readingStatus: ReadingStatus? = null,
    val author: String? = null,
    val tags: List<String>? = null,
    val rating: Int? = null,
    val votes: Int? = null,
    val chapterCount: Int? = null,
    val readCount: Int? = null,
    val downloadedCount: Int? = null
) {
    /**
     * Get the effective provider name for rating formatting
     */
    val effectiveProviderName: String?
        get() = providerName ?: novel.apiName

    /**
     * Format the rating using the specified format
     */
    fun getFormattedRating(format: RatingFormat): String? {
        return rating?.let { RatingUtils.format(it, format, effectiveProviderName) }
    }
}

// ================================================================
// MAIN ACTION SHEET
// ================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelActionSheet(
    data: NovelActionSheetData,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit,
    onContinueReading: () -> Unit,
    onAddToLibrary: ((ReadingStatus) -> Unit)?,
    onRemoveFromLibrary: (() -> Unit)?,
    onStatusChange: ((ReadingStatus) -> Unit)? = null,
    onRemoveFromHistory: (() -> Unit)? = null,
    // Slice-03.3: suggest-only duplicate lookup (null = section hidden).
    onFindDuplicates: (suspend (Novel) -> List<LibraryItem>)? = null,
    onOpenDuplicate: (LibraryItem) -> Unit = {},
    // Slice-04.1b: post-migration navigation (url + provider of the target).
    onMigrated: ((String, String) -> Unit)? = null,
    // Slice-04.3: open a novel found by alternative-source search.
    onOpenNovel: (Novel) -> Unit = {}
) {
    var showCoverZoom by remember { mutableStateOf(false) }
    var showSynopsisOverlay by remember { mutableStateOf(false) }
    var statusPickerMode by remember { mutableStateOf<StatusPickerMode?>(null) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }
    // Slice-03.3: duplicate candidates (null = not searched yet).
    var duplicates by remember(data.novel.url) { mutableStateOf<List<LibraryItem>?>(null) }
    var findingDuplicates by remember(data.novel.url) { mutableStateOf(false) }
    // Slice-04.1b: work identity for migration + wizard target.
    var workId by remember(data.novel.url) { mutableStateOf<Long?>(null) }
    var migrateTarget by remember(data.novel.url) { mutableStateOf<LibraryItem?>(null) }
    // Slice-04.2: merge target.
    var mergeTarget by remember(data.novel.url) { mutableStateOf<LibraryItem?>(null) }
    // Slice-04.3: alternative-source search sheet.
    var showAlternatives by remember(data.novel.url) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Dialogs
    if (showCoverZoom && !data.novel.posterUrl.isNullOrBlank()) {
        CoverZoomDialog(
            imageUrl = data.novel.posterUrl!!,
            title = data.novel.name,
            onDismiss = { showCoverZoom = false }
        )
    }

    if (showSynopsisOverlay && !data.synopsis.isNullOrBlank()) {
        SynopsisOverlay(
            title = data.novel.name,
            synopsis = data.synopsis,
            onDismiss = { showSynopsisOverlay = false }
        )
    }

    if (statusPickerMode != null) {
        val pickerMode = statusPickerMode
        StatusPickerDialog(
            currentStatus = data.readingStatus ?: ReadingStatus.READING,
            title = if (pickerMode == StatusPickerMode.ADD) "Add to Library" else "Reading Status",
            showRemove = pickerMode == StatusPickerMode.UPDATE && onRemoveFromLibrary != null,
            onStatusSelect = { status ->
                statusPickerMode = null
                if (pickerMode == StatusPickerMode.ADD) {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        onAddToLibrary?.invoke(status)
                    }
                } else {
                    onStatusChange?.invoke(status)
                }
            },
            onRemove = {
                statusPickerMode = null
                showRemoveConfirmation = true
            },
            onDismiss = { statusPickerMode = null }
        )
    }

    if (showRemoveConfirmation) {
        RemoveConfirmationDialog(
            novelName = data.novel.name,
            onConfirm = {
                showRemoveConfirmation = false
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                    onRemoveFromLibrary?.invoke()
                }
            },
            onDismiss = { showRemoveConfirmation = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { CompactDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            CompactHeader(
                data = data,
                onCoverClick = { showCoverZoom = true }
            )

            InlineStats(data = data)

            if (!data.tags.isNullOrEmpty()) {
                CompactTags(tags = data.tags)
            }

            if (!data.synopsis.isNullOrBlank()) {
                CompactSynopsis(
                    synopsis = data.synopsis,
                    onExpand = { showSynopsisOverlay = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            CompactActions(
                hasLastChapter = data.lastChapterName != null,
                isInLibrary = data.isInLibrary,
                currentStatus = data.readingStatus,
                onContinueReading = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        onContinueReading()
                    }
                },
                onViewDetails = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        onViewDetails()
                    }
                },
                onAddToLibrary = {
                    statusPickerMode = StatusPickerMode.ADD
                },
                onOpenStatusPicker = { statusPickerMode = StatusPickerMode.UPDATE },
                onRemoveFromHistory = onRemoveFromHistory?.let { callback ->
                    {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            callback()
                        }
                    }
                }
            )

            // Slice-03.3: suggest-only duplicate lookup for library entries.
            if (data.isInLibrary && onFindDuplicates != null) {
                                DuplicatesSection(
                    duplicates = duplicates,
                    finding = findingDuplicates,
                    workId = workId,
                    onFind = {                        findingDuplicates = true
                        scope.launch {
                            try {
                                duplicates = onFindDuplicates(data.novel)
                                workId = RepositoryProvider.getWorkRepository()
                                    .getWorkForNovel(data.novel.url)?.id
                            } catch (_: Exception) {
                                duplicates = emptyList()
                            } finally {
                                findingDuplicates = false
                            }
                        }
                    },
                    onOpen = { item ->
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onOpenDuplicate(item)
                        }
                    },
                    onMoveHere = { item -> migrateTarget = item },
                    onMergeHere = { item -> mergeTarget = item },
                    onSearchAlternatives = { showAlternatives = true }
                )
            }

            // Slice-04.3: alternative-source search.
            if (showAlternatives) {
                AlternativeSourcesSheet(
                    query = data.novel.name,
                    excludeProvider = data.novel.apiName,
                    onDismiss = { showAlternatives = false },
                    onOpen = { novel ->
                        showAlternatives = false
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onOpenNovel(novel)
                        }
                    }
                )
            }

            // Slice-04.2: merge dialog.
            val mergeItem = mergeTarget
            if (mergeItem != null && workId != null && mergeItem.workId != null) {
                MergeDialog(
                    currentWorkId = workId!!,
                    currentUrl = data.novel.url,
                    candidate = mergeItem,
                    onDismiss = { mergeTarget = null },
                    onMerged = {
                        mergeTarget = null
                        scope.launch {
                            // Refresh candidates (the merged entry is gone)…
                            try {
                                onFindDuplicates?.let { duplicates = it(data.novel) }
                            } catch (_: Exception) {
                            }
                            // …and close the sheet if our own row was removed.
                            val stillThere = try {
                                RepositoryProvider.getLibraryRepository()
                                    .getLibraryItem(data.novel.url) != null
                            } catch (_: Exception) {
                                true
                            }
                            if (!stillThere) {
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                    }
                )
            }

            // Slice-04.1b: migration wizard.
            val migrateItem = migrateTarget
            if (migrateItem != null && workId != null) {
                MigrateDialog(
                    workId = workId!!,
                    fromUrl = data.novel.url,
                    target = migrateItem,
                    onDismiss = { migrateTarget = null },
                    onMigrated = { url, provider ->
                        migrateTarget = null
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            if (onMigrated != null) {
                                onMigrated(url, provider)
                            } else {
                                onOpenDuplicate(migrateItem)
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ================================================================
// COMPACT COMPONENTS
// ================================================================

@Composable
private fun CompactDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        )
    }
}

@Composable
private fun CompactHeader(
    data: NovelActionSheetData,
    onCoverClick: () -> Unit
) {
    // Get rating format from preferences - use RepositoryProvider for consistency
    val preferencesManager = remember { RepositoryProvider.getPreferencesManager() }
    val appSettings by preferencesManager.appSettings.collectAsState()
    val ratingFormat = appSettings.ratingFormat

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Small cover
        Box {
            Card(
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(2f / 3f)
                    .shadow(6.dp, RoundedCornerShape(8.dp))
                    .clickable { onCoverClick() },
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Box {
                    if (!data.novel.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = data.novel.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            if (data.isInLibrary) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .shadow(2.dp, CircleShape)
                        .background(ActionSheetColors.Pink, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = data.novel.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!data.author.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = data.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                val provider = data.providerName ?: data.novel.apiName
                if (provider.isNotBlank()) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = provider,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (data.rating != null && data.rating > 0) {
                    val formattedRating = data.getFormattedRating(ratingFormat)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = ActionSheetColors.Star
                        )
                        Text(
                            text = formattedRating ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ActionSheetColors.Star
                        )
                        if (data.votes != null) {
                            Text(
                                text = "(${formatVotesCompact(data.votes)})",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (data.isInLibrary && data.readingStatus != null) {
                    val color = ActionSheetColors.forStatus(data.readingStatus)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getStatusIcon(data.readingStatus),
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = color
                        )
                        Text(
                            text = data.readingStatus.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = color
                        )
                    }
                }
            }

            if (!data.lastChapterName.isNullOrBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdded,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = data.lastChapterName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// Slice-04.3: alternative-source search — finds the same title on other
// providers (e.g. when the current source is dead). Opening a hit goes to
// its details, where duplicate attach/merge take over.
@Composable
private fun AlternativeSourcesSheet(
    query: String,
    excludeProvider: String,
    onDismiss: () -> Unit,
    onOpen: (Novel) -> Unit
) {
    var results by remember(query) { mutableStateOf<Map<String, List<Novel>>>(emptyMap()) }
    var searching by remember(query) { mutableStateOf(true) }

    LaunchedEffect(query) {
        searching = true
        try {
            RepositoryProvider.getNovelRepository()
                .searchAllStreaming(query)
                .collect { (providerName, result) ->
                    if (providerName == excludeProvider) return@collect
                    result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { hits ->
                        results = results + (providerName to hits.take(5))
                    }
                }
        } catch (_: Exception) {
            // Partial results stand.
        } finally {
            searching = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Other sources",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\"$query\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (searching && results.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = "Searching sources…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (!searching && results.isEmpty()) {
                    Text(
                        text = "No matches on other sources",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                results.forEach { (providerName, hits) ->
                    Text(
                        text = providerName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    hits.forEach { novel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpen(novel) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = novel.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

// Slice-04.2: merge dialog — combines two entries of the same work into
// one shelf row. Projections, history, read marks and downloads stay with
// their URLs; only the duplicate row is removed.
private sealed interface MergePhase {
    data object Loading : MergePhase
    data class Pick(
        val currentIndex: Int,
        val currentAddedAt: Long,
        val candidateIndex: Int,
        val candidateAddedAt: Long,
        val keepCurrent: Boolean
    ) : MergePhase
    data object Working : MergePhase
    data class Done(val removedRows: Int, val projections: Int) : MergePhase
    data class Failed(val message: String) : MergePhase
}

@Composable
private fun MergeDialog(
    currentWorkId: Long,
    currentUrl: String,
    candidate: LibraryItem,
    onDismiss: () -> Unit,
    onMerged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember(candidate.novel.url) { mutableStateOf<MergePhase>(MergePhase.Loading) }

    LaunchedEffect(candidate.novel.url) {
        try {
            val libs = RepositoryProvider.getLibraryRepository()
            val current = libs.getEntry(currentUrl)
            val other = libs.getEntry(candidate.novel.url)
            if (current == null || other == null) {
                phase = MergePhase.Failed("One of the entries left the library")
                return@LaunchedEffect
            }
            phase = MergePhase.Pick(
                currentIndex = current.lastReadChapterIndex,
                currentAddedAt = current.addedAt,
                candidateIndex = other.lastReadChapterIndex,
                candidateAddedAt = other.addedAt,
                keepCurrent = WorkMigration.suggestKeepRow(
                    current.lastReadChapterIndex, current.addedAt,
                    other.lastReadChapterIndex, other.addedAt
                )
            )
        } catch (e: Exception) {
            phase = MergePhase.Failed(e.message ?: "Could not load entries")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Merge duplicate entries?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Both sources stay attached to one entry. Only the extra shelf row is removed — progress, history and downloads are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (val p = phase) {
                    MergePhase.Loading, MergePhase.Working -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                text = if (p == MergePhase.Loading) "Loading…" else "Merging…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is MergePhase.Pick -> {
                        MergeKeepRow(
                            label = "Keep this entry",
                            sub = "Read to chapter ${p.currentIndex + 1}",
                            selected = p.keepCurrent,
                            onSelect = { phase = p.copy(keepCurrent = true) }
                        )
                        MergeKeepRow(
                            label = "Keep ${candidate.novel.apiName} entry",
                            sub = "Read to chapter ${p.candidateIndex + 1}",
                            selected = !p.keepCurrent,
                            onSelect = { phase = p.copy(keepCurrent = false) }
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                            TextButton(onClick = {
                                scope.launch {
                                    phase = MergePhase.Working
                                    try {
                                        val repo = RepositoryProvider.getWorkRepository()
                                        val candidateWorkId = candidate.workId
                                            ?: throw IllegalStateException("Candidate has no work")
                                        val (survivor, absorbed, keepUrl) = if (p.keepCurrent) {
                                            Triple(currentWorkId, candidateWorkId, currentUrl)
                                        } else {
                                            Triple(candidateWorkId, currentWorkId, candidate.novel.url)
                                        }
                                        when (val outcome = repo.mergeWorks(survivor, absorbed, keepUrl)) {
                                            is WorkRepository.MergeOutcome.Merged -> {
                                                phase = MergePhase.Done(
                                                    outcome.info.removedRows,
                                                    outcome.info.projectionCount
                                                )
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Merged — ${outcome.info.projectionCount} sources, one entry",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                                onMerged()
                                            }
                                            WorkRepository.MergeOutcome.SameWork ->
                                                phase = MergePhase.Failed("Already the same entry")
                                        }
                                    } catch (e: Exception) {
                                        phase = MergePhase.Failed(e.message ?: "Merge failed")
                                    }
                                }
                            }) { Text("Merge") }
                        }
                    }
                    is MergePhase.Done -> {
                        Text(
                            text = "Merged! Removed ${p.removedRows} duplicate row(s); " +
                                "${p.projections} source(s) attached.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onDismiss) { Text("Close") }
                        }
                    }
                    is MergePhase.Failed -> {
                        Text(
                            text = p.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onDismiss) { Text("Close") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeKeepRow(
    label: String,
    sub: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = selected, onCheckedChange = { onSelect() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Slice-04.1b: migration wizard — moves this work's library state to the
// target entry (position by chapter index, shelf, history, optional read
// marks). Everything applies in one transaction; downloads stay behind.
private sealed interface MigratePhase {
    data object Loading : MigratePhase
    data class Preview(
        val sourceReadIndex: Int,
        val sourceReadCount: Int,
        val targetChapters: List<Chapter>,
        val flags: WorkRepository.MigrateFlags
    ) : MigratePhase
    data object Working : MigratePhase
    data class Done(val info: WorkRepository.MovedInfo) : MigratePhase
    data object Switched : MigratePhase
    data class Failed(val message: String) : MigratePhase
}

@Composable
private fun MigrateDialog(
    workId: Long,
    fromUrl: String,
    target: LibraryItem,
    onDismiss: () -> Unit,
    onMigrated: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var phase by remember(target.novel.url) { mutableStateOf<MigratePhase>(MigratePhase.Loading) }

    LaunchedEffect(target.novel.url) {
        try {
            val libs = RepositoryProvider.getLibraryRepository()
            val novels = RepositoryProvider.getNovelRepository()
            val history = RepositoryProvider.getHistoryRepository()
            val source = libs.getEntry(fromUrl)
            val provider = novels.getProvider(target.novel.apiName)
                ?: throw IllegalArgumentException("Provider ${target.novel.apiName} unavailable")
            val details = novels.loadNovelDetails(provider, target.novel.url).getOrThrow()
            phase = MigratePhase.Preview(
                sourceReadIndex = source?.lastReadChapterIndex ?: -1,
                sourceReadCount = history.getReadChapterCount(fromUrl),
                targetChapters = details.chapters,
                flags = WorkRepository.MigrateFlags()
            )
        } catch (e: Exception) {
            phase = MigratePhase.Failed(e.message ?: "Could not load target novel")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Move to ${target.novel.name}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${target.novel.apiName} · ${target.novel.url}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                when (val p = phase) {
                    MigratePhase.Loading, MigratePhase.Working -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                text = if (p == MigratePhase.Loading) "Loading target…" else "Moving…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is MigratePhase.Preview -> {
                        val position = if (p.flags.movePosition) {
                            WorkMigration.mapPosition(p.sourceReadIndex, p.targetChapters)
                        } else null
                        Text(
                            text = "Target has ${p.targetChapters.size} chapters.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = position?.let { "Continue from \"${it.newName}\"" }
                                ?: "No reading position to move",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MigrateFlagRow(
                            label = "Reading position",
                            checked = p.flags.movePosition,
                            onChange = {
                                phase = p.copy(flags = p.flags.copy(movePosition = it))
                            }
                        )
                        MigrateFlagRow(
                            label = "Shelf & status",
                            checked = p.flags.moveShelf,
                            onChange = {
                                phase = p.copy(flags = p.flags.copy(moveShelf = it))
                            }
                        )
                        MigrateFlagRow(
                            label = "History entry",
                            checked = p.flags.moveHistory,
                            onChange = {
                                phase = p.copy(flags = p.flags.copy(moveHistory = it))
                            }
                        )
                        MigrateFlagRow(
                            label = "Read checkmarks (by order)",
                            checked = p.flags.moveReadMarks,
                            onChange = {
                                phase = p.copy(flags = p.flags.copy(moveReadMarks = it))
                            }
                        )
                        Text(
                            text = "Downloads stay on the old source.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                            TextButton(onClick = {
                                scope.launch {
                                    phase = MigratePhase.Working
                                    try {
                                        val repo = RepositoryProvider.getWorkRepository()
                                        when (val outcome = repo.migrateWork(
                                            workId, fromUrl, target.novel,
                                            p.targetChapters, p.flags
                                        )) {
                                            is WorkRepository.MigrateOutcome.Moved ->
                                                phase = MigratePhase.Done(outcome.info)
                                            is WorkRepository.MigrateOutcome.SwitchedDefault ->
                                                phase = MigratePhase.Switched
                                            WorkRepository.MigrateOutcome.TargetInOtherWork ->
                                                phase = MigratePhase.Failed(
                                                    "Already part of another entry — merge arrives next."
                                                )
                                        }
                                    } catch (e: Exception) {
                                        phase = MigratePhase.Failed(
                                            e.message ?: "Migration failed"
                                        )
                                    }
                                }
                            }) { Text("Move") }
                        }
                    }
                    is MigratePhase.Done -> {
                        Text(
                            text = buildString {
                                append("Moved! ")
                                p.info.position?.let { append("Continue from \"${it.newName}\". ") }
                                if (p.info.readMarks > 0) append("${p.info.readMarks} chapters marked read. ")
                                if (p.info.historyMoved) append("History moved.")
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onDismiss) { Text("Close") }
                            TextButton(onClick = {
                                onMigrated(target.novel.url, target.novel.apiName)
                            }) { Text("Open migrated novel") }
                        }
                    }
                    MigratePhase.Switched -> {
                        Text(
                            text = "Already attached — switched the default source.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onDismiss) { Text("Close") }
                            TextButton(onClick = {
                                onMigrated(target.novel.url, target.novel.apiName)
                            }) { Text("Open") }
                        }
                    }
                    is MigratePhase.Failed -> {
                        Text(
                            text = p.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onDismiss) { Text("Close") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrateFlagRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

// Slice-03.3: suggest-only duplicate entries (same title, other sources).
// Candidates stay suggestions — opening navigates, never merges.
@Composable
private fun DuplicatesSection(
    duplicates: List<LibraryItem>?,
    finding: Boolean,
    workId: Long?,
    onFind: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onMoveHere: (LibraryItem) -> Unit,
    onMergeHere: (LibraryItem) -> Unit,
    onSearchAlternatives: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp)
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        when {
            finding -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Searching other sources…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            duplicates == null -> {
                OutlinedButton(onClick = onFind) {
                    Text("Find duplicates on other sources")
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onSearchAlternatives) {
                    Text("Search all sources for this title")
                }
            }
            duplicates.isEmpty() -> {
                Text(
                    text = "No duplicate entries found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onSearchAlternatives) {
                    Text("Search all sources for this title")
                }
            }
            else -> {
                Text(
                    text = "Same novel on ${duplicates.size} other source${if (duplicates.size == 1) "" else "s"}:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                duplicates.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpen(item) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.novel.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.novel.apiName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Slice-04.1b: migrate this work's state here.
                        if (workId != null) {
                            TextButton(onClick = { onMoveHere(item) }) {
                                Text(
                                    "Move here",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        // Slice-04.2: merge the two entries (keeps one row).
                        if (workId != null && item.workId != null && item.workId != workId) {
                            TextButton(onClick = { onMergeHere(item) }) {
                                Text(
                                    "Merge",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineStats(data: NovelActionSheetData) {
    val hasStats = data.chapterCount != null || data.readCount != null || data.downloadedCount != null
    if (!hasStats) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (data.chapterCount != null) {
            InlineStat(
                icon = Icons.Rounded.MenuBook,
                value = "${data.chapterCount}",
                label = "ch",
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (data.readCount != null && data.readCount > 0) {
            val percentage = if (data.chapterCount != null && data.chapterCount > 0) {
                " (${data.readCount * 100 / data.chapterCount}%)"
            } else ""

            InlineStat(
                icon = Icons.Rounded.Visibility,
                value = "${data.readCount}$percentage",
                label = "read",
                color = ActionSheetColors.Success
            )
        }

        if (data.downloadedCount != null && data.downloadedCount > 0) {
            InlineStat(
                icon = Icons.Rounded.DownloadDone,
                value = "${data.downloadedCount}",
                label = "saved",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun InlineStat(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactTags(tags: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.take(4).forEach { tag ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = tag,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (tags.size > 4) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "+${tags.size - 4}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CompactSynopsis(
    synopsis: String,
    onExpand: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onExpand),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = synopsis,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "more",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CompactActions(
    hasLastChapter: Boolean,
    isInLibrary: Boolean,
    currentStatus: ReadingStatus?,
    onContinueReading: () -> Unit,
    onViewDetails: () -> Unit,
    onAddToLibrary: () -> Unit,
    onOpenStatusPicker: () -> Unit,
    onRemoveFromHistory: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactPrimaryButton(
            text = if (hasLastChapter) "Continue Reading" else "Start Reading",
            icon = Icons.Rounded.PlayArrow,
            onClick = onContinueReading
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactSecondaryButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.MenuBook,
                text = "Details",
                onClick = onViewDetails
            )

            if (isInLibrary) {
                // Status selector button
                StatusSelectorButton(
                    modifier = Modifier.weight(1f),
                    currentStatus = currentStatus ?: ReadingStatus.READING,
                    onClick = onOpenStatusPicker
                )
            } else {
                CompactSecondaryButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.FavoriteBorder,
                    text = "Add",
                    accentColor = ActionSheetColors.Pink,
                    onClick = onAddToLibrary
                )
            }
        }

        if (onRemoveFromHistory != null) {
            Surface(
                onClick = onRemoveFromHistory,
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HistoryToggleOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remove from History",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CompactSecondaryButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    accentColor: Color? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    val containerColor = if (accentColor != null) {
        accentColor.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .height(40.dp)
            .scale(scale),
        color = containerColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun StatusSelectorButton(
    modifier: Modifier = Modifier,
    currentStatus: ReadingStatus,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    val statusColor = ActionSheetColors.forStatus(currentStatus)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .height(40.dp)
            .scale(scale),
        color = statusColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getStatusIcon(currentStatus),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = statusColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = currentStatus.displayName(),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = statusColor.copy(alpha = 0.7f)
            )
        }
    }
}

// ================================================================
// STATUS PICKER DIALOG
// ================================================================

@Composable
private fun StatusPickerDialog(
    currentStatus: ReadingStatus,
    title: String,
    showRemove: Boolean,
    onStatusSelect: (ReadingStatus) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status options
                ReadingStatus.entries.forEach { status ->
                    StatusOptionItem(
                        status = status,
                        isSelected = status == currentStatus,
                        onClick = { onStatusSelect(status) }
                    )
                }

                if (showRemove) {
                    Spacer(modifier = Modifier.height(4.dp))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    RemoveOptionItem(onClick = onRemove)

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusOptionItem(
    status: ReadingStatus,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val statusColor = ActionSheetColors.forStatus(status)

    Surface(
        onClick = onClick,
        color = if (isSelected) statusColor.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getStatusIcon(status),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = statusColor
                        )
                    }
                }
                Text(
                    text = status.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) statusColor else MaterialTheme.colorScheme.onSurface
                )
            }

            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = statusColor,
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveOptionItem(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = ActionSheetColors.Error.copy(alpha = 0.1f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ActionSheetColors.Error
                    )
                }
            }
            Text(
                text = "Remove from Library",
                style = MaterialTheme.typography.bodyMedium,
                color = ActionSheetColors.Error
            )
        }
    }
}

// ================================================================
// DIALOGS
// ================================================================

@Composable
private fun CoverZoomDialog(
    imageUrl: String,
    title: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        onTap = { onDismiss() }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }

            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )

            Text(
                text = "Double-tap to zoom",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SynopsisOverlay(
    title: String,
    synopsis: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(0.9f)
                    .clickable(enabled = false, onClick = {}),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    Text(
                        text = synopsis,
                        modifier = Modifier
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoveConfirmationDialog(
    novelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.DeleteForever,
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Remove from Library?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = novelName,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Progress will be preserved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Remove", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ================================================================
// UTILITIES
// ================================================================

private fun formatVotesCompact(votes: Int): String = when {
    votes >= 1_000_000 -> "${votes / 1_000_000}M"
    votes >= 1_000 -> "${votes / 1_000}K"
    else -> votes.toString()
}

private fun getStatusIcon(status: ReadingStatus): ImageVector = when (status) {
    ReadingStatus.READING -> Icons.Default.MenuBook
    ReadingStatus.SPICY -> Icons.Default.LocalFireDepartment
    ReadingStatus.COMPLETED -> Icons.Default.CheckCircle
    ReadingStatus.ON_HOLD -> Icons.Default.Pause
    ReadingStatus.PLAN_TO_READ -> Icons.Default.Schedule
    ReadingStatus.DROPPED -> Icons.Default.Cancel
}
