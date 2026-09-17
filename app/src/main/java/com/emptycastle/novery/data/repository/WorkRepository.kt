package com.emptycastle.novery.data.repository

import androidx.room.withTransaction
import com.emptycastle.novery.data.local.NovelDatabase
import com.emptycastle.novery.data.local.dao.HistoryDao
import com.emptycastle.novery.data.local.dao.LibraryDao
import com.emptycastle.novery.data.local.dao.OfflineDao
import com.emptycastle.novery.data.local.dao.WorkDao
import com.emptycastle.novery.data.local.entity.ReadChapterEntity
import com.emptycastle.novery.data.local.entity.WorkEntity
import com.emptycastle.novery.data.local.entity.WorkProjectionEntity
import com.emptycastle.novery.domain.model.Chapter
import com.emptycastle.novery.domain.model.Novel
import com.emptycastle.novery.domain.model.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Slice-03: work identity + projection management (Kototoro-style entity
 * layer, novel-app edition).
 *
 * - One [WorkEntity] per real novel; source entries attach as projections.
 * - Favorites/history/stats stay keyed by novelUrl for now (slice 4 moves
 *   merge semantics, never silently).
 */
class WorkRepository(
    private val workDao: WorkDao,
    private val libraryDao: LibraryDao,
    private val historyDao: HistoryDao? = null,
    private val offlineDao: OfflineDao? = null,
    private val db: NovelDatabase? = null
) {

    /**
     * Idempotent backfill: every library row gets exactly one work with one
     * projection. Safe to run on every app start; only fills gaps.
     * @return number of works created.
     */
    suspend fun ensureBackfilled(): Int = withContext(Dispatchers.IO) {
        var created = 0
        for (entity in libraryDao.getAll()) {
            if (workDao.getProjectionByUrl(entity.url) != null) continue
            val workId = workDao.insertWork(WorkEntity(defaultNovelUrl = entity.url))
            workDao.insertProjection(
                WorkProjectionEntity(
                    workId = workId,
                    novelUrl = entity.url,
                    providerName = entity.apiName
                )
            )
            created++
        }
        created
    }

    suspend fun getWork(id: Long): WorkEntity? = withContext(Dispatchers.IO) {
        workDao.getWork(id)
    }

    suspend fun getProjections(workId: Long): List<WorkProjectionEntity> =
        withContext(Dispatchers.IO) {
            workDao.getProjections(workId)
        }

    suspend fun getWorkForNovel(novelUrl: String): WorkEntity? =
        withContext(Dispatchers.IO) {
            val projection = workDao.getProjectionByUrl(novelUrl) ?: return@withContext null
            workDao.getWork(projection.workId)
        }

    /**
     * Sets the preferred projection for a work. The URL must already be an
     * attached projection — never invents bindings.
     * @return false when the URL is not attached to the work.
     */
    suspend fun setDefaultProjection(workId: Long, novelUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            val projections = workDao.getProjections(workId)
            if (projections.none { it.novelUrl == novelUrl }) return@withContext false
            workDao.setDefaultNovelUrl(workId, novelUrl)
            true
        }

    /**
     * Resolves which projection to open for a work: the explicit default
     * when it is still attached, otherwise the oldest attached projection.
     */
    suspend fun resolveDefaultProjection(workId: Long): WorkProjectionEntity? =
        withContext(Dispatchers.IO) {
            val work = workDao.getWork(workId) ?: return@withContext null
            val projections = workDao.getProjections(workId)
            if (projections.isEmpty()) return@withContext null
            WorkTitles.resolveDefault(projections, work.defaultNovelUrl)
        }

    // =====================================================================
    // SLICE-04.2: ATTACH / DETACH / MERGE
    // =====================================================================

    sealed interface AttachOutcome {
        data object Attached : AttachOutcome
        data object AlreadyAttached : AttachOutcome
        /** URL belongs to a different work — merge explicitly instead. */
        data object InOtherWork : AttachOutcome
    }

    /**
     * Attaches a novel as an alternate source of a work WITHOUT creating a
     * library row. Used by the duplicate warning ("add as alternate
     * source") so users stop accumulating duplicate rows.
     */
    suspend fun attachProjection(workId: Long, novel: Novel): AttachOutcome =
        withContext(Dispatchers.IO) {
            val work = workDao.getWork(workId) ?: throw IllegalArgumentException("Work not found")
            if (workDao.getProjections(workId).any { it.novelUrl == novel.url }) {
                return@withContext AttachOutcome.AlreadyAttached
            }
            if (workDao.getProjectionByUrl(novel.url) != null) {
                return@withContext AttachOutcome.InOtherWork
            }
            tx {
                workDao.insertProjection(
                    WorkProjectionEntity(
                        workId = workId,
                        novelUrl = novel.url,
                        providerName = novel.apiName
                    )
                )
                // First projection ever attached becomes the default.
                if (work.defaultNovelUrl == null) {
                    workDao.setDefaultNovelUrl(workId, novel.url)
                }
            }
            AttachOutcome.Attached
        }

    sealed interface DetachOutcome {
        data object Detached : DetachOutcome
        data object LastProjection : DetachOutcome
    }

    /**
     * Removes a projection's binding from a work. Never deletes library
     * rows, history, downloads or the source entry itself. A detached
     * projection that still has a library row becomes a standalone work on
     * the next backfill (split-out for free).
     */
    suspend fun detachProjection(workId: Long, novelUrl: String): DetachOutcome =
        withContext(Dispatchers.IO) {
            val projections = workDao.getProjections(workId)
            val target = projections.find { it.novelUrl == novelUrl }
                ?: throw IllegalArgumentException("Projection not attached")
            if (projections.size <= 1) {
                return@withContext DetachOutcome.LastProjection
            }
            tx {
                workDao.deleteProjection(target.id)
                val work = workDao.getWork(workId)
                if (work?.defaultNovelUrl == novelUrl) {
                    val remaining = workDao.getProjections(workId)
                    workDao.setDefaultNovelUrl(
                        workId,
                        remaining.minByOrNull { it.addedAt }?.novelUrl
                    )
                }
            }
            DetachOutcome.Detached
        }

    data class MergedInfo(
        val keptRowUrl: String,
        val removedRows: Int,
        val projectionCount: Int
    )

    sealed interface MergeOutcome {
        data class Merged(val info: MergedInfo) : MergeOutcome
        data object SameWork : MergeOutcome
    }

    /**
     * Merges [absorbedWorkId] into [survivingWorkId], keeping the library
     * row [keepRowUrl] (all other rows of both works are removed from the
     * shelf). Projections, history, read marks, bookmarks and downloads
     * stay attached to their (surviving) URLs — only shelf rows move, so
     * nothing the user created is lost except the duplicate row itself.
     */
    suspend fun mergeWorks(
        survivingWorkId: Long,
        absorbedWorkId: Long,
        keepRowUrl: String
    ): MergeOutcome = withContext(Dispatchers.IO) {
        if (survivingWorkId == absorbedWorkId) {
            return@withContext MergeOutcome.SameWork
        }
        val survivor = workDao.getProjections(survivingWorkId)
        val absorbed = workDao.getProjections(absorbedWorkId)
        if (survivor.isEmpty() || absorbed.isEmpty()) {
            throw IllegalArgumentException("Cannot merge empty works")
        }
        val keepAttached = (survivor + absorbed).any { it.novelUrl == keepRowUrl }
        require(keepAttached) { "Kept row is not part of either work" }
        require(libraryDao.getByUrl(keepRowUrl) != null) { "Kept row is not in the library" }

        var removed = 0
        tx {
            for (projection in absorbed) {
                workDao.deleteProjection(projection.id)
                workDao.insertProjection(projection.copy(id = 0, workId = survivingWorkId))
                if (projection.novelUrl != keepRowUrl) {
                    if (libraryDao.getByUrl(projection.novelUrl) != null) {
                        libraryDao.delete(projection.novelUrl)
                        removed++
                    }
                }
            }
            for (projection in survivor) {
                if (projection.novelUrl != keepRowUrl &&
                    libraryDao.getByUrl(projection.novelUrl) != null
                ) {
                    libraryDao.delete(projection.novelUrl)
                    removed++
                }
            }
            workDao.deleteWork(absorbedWorkId)
            workDao.setDefaultNovelUrl(survivingWorkId, keepRowUrl)
        }
        val count = workDao.projectionCount(survivingWorkId)
        MergeOutcome.Merged(MergedInfo(keepRowUrl, removed, count))
    }

    // =====================================================================
    // SLICE-04.1b: MIGRATION
    // =====================================================================

    data class MigrateFlags(
        val movePosition: Boolean = true,
        val moveShelf: Boolean = true,
        val moveHistory: Boolean = true,
        val moveReadMarks: Boolean = false
    )

    data class MovedInfo(
        val position: WorkMigration.PositionMapping?,
        val readMarks: Int,
        val historyMoved: Boolean
    )

    sealed interface MigrateOutcome {
        data class Moved(val info: MovedInfo) : MigrateOutcome
        data class SwitchedDefault(val novelUrl: String) : MigrateOutcome
        /** Target belongs to another work — merge it explicitly (4.2). */
        data object TargetInOtherWork : MigrateOutcome
    }

    private suspend fun <R> tx(block: suspend () -> R): R {
        val database = db
        return if (database == null) block()
        else database.withTransaction { block() }
    }

    /**
     * Moves a work's library state from [fromUrl] to [target].
     *
     * Chapter URLs are source-specific, so the reading position maps by
     * chapter INDEX; read checkmarks only move when explicitly enabled
     * (matched by order). History moves only when the target has none or
     * an older one. Downloads stay behind (content differs per source).
     * Everything runs in one Room transaction when a database is present.
     */
    suspend fun migrateWork(
        workId: Long,
        fromUrl: String,
        target: Novel,
        targetChapters: List<Chapter>,
        flags: MigrateFlags = MigrateFlags()
    ): MigrateOutcome = withContext(Dispatchers.IO) {
        val toUrl = target.url
        require(toUrl != fromUrl) { "Target is the source entry" }

        val projections = workDao.getProjections(workId)
        require(projections.any { it.novelUrl == fromUrl }) {
            "Source entry is not attached to this work"
        }

        // Already attached → safe source-switch, no data moves.
        if (projections.any { it.novelUrl == toUrl }) {
            workDao.setDefaultNovelUrl(workId, toUrl)
            return@withContext MigrateOutcome.SwitchedDefault(toUrl)
        }
        // Attached elsewhere → never steal; merge explicitly (4.2).
        if (workDao.getProjectionByUrl(toUrl) != null) {
            return@withContext MigrateOutcome.TargetInOtherWork
        }

        val source = libraryDao.getByUrl(fromUrl)
            ?: throw IllegalArgumentException("Source entry is not in the library")

        val position = if (flags.movePosition) {
            WorkMigration.mapPosition(source.lastReadChapterIndex, targetChapters)
        } else null

        val oldReadCount = historyDao?.getReadCountForNovel(fromUrl) ?: 0
        val readMarks = WorkMigration.readMarksToTransfer(
            oldReadCount, targetChapters, flags.moveReadMarks
        )

        val sourceHistory = historyDao?.getByNovelUrl(fromUrl)
        val targetHistory = historyDao?.getByNovelUrl(toUrl)
        val historyMoves = flags.moveHistory &&
            WorkMigration.shouldMoveHistory(
                sourceHistory?.timestamp, targetHistory?.timestamp
            )

        tx {
            val now = System.currentTimeMillis()
            libraryDao.insert(
                source.copy(
                    url = toUrl,
                    name = target.name,
                    posterUrl = target.posterUrl ?: source.posterUrl,
                    apiName = target.apiName,
                    latestChapter = target.latestChapter,
                    readingStatus = if (flags.moveShelf) {
                        source.readingStatus
                    } else {
                        ReadingStatus.READING.name
                    },
                    totalChapterCount = targetChapters.size,
                    acknowledgedChapterCount = targetChapters.size,
                    lastCheckedAt = now,
                    lastReadChapterIndex = position?.newIndex ?: -1,
                    unreadChapterCount = if (position != null) {
                        (targetChapters.size - position.newIndex - 1).coerceAtLeast(0)
                    } else {
                        0
                    },
                    lastChapterUrl = position?.newUrl,
                    lastChapterName = position?.newName ?: source.lastChapterName,
                    lastScrollIndex = 0,
                    lastScrollOffset = 0
                )
            )

            workDao.insertProjection(
                WorkProjectionEntity(
                    workId = workId,
                    novelUrl = toUrl,
                    providerName = target.apiName
                )
            )
            workDao.setDefaultNovelUrl(workId, toUrl)

            if (historyMoves && sourceHistory != null && historyDao != null) {
                historyDao.insert(
                    sourceHistory.copy(
                        novelUrl = toUrl,
                        novelName = target.name,
                        posterUrl = target.posterUrl ?: sourceHistory.posterUrl,
                        chapterName = position?.newName ?: sourceHistory.chapterName,
                        chapterUrl = position?.newUrl ?: sourceHistory.chapterUrl,
                        apiName = target.apiName
                    )
                )
                historyDao.deleteByNovelUrl(fromUrl)
            }

            if (readMarks.isNotEmpty() && historyDao != null) {
                historyDao.markChaptersRead(
                    readMarks.map { chapter ->
                        ReadChapterEntity(
                            chapterUrl = chapter.url,
                            novelUrl = toUrl
                        )
                    }
                )
            }

            libraryDao.delete(fromUrl)
            projections.find { it.novelUrl == fromUrl }?.let {
                workDao.deleteProjection(it.id)
            }
            offlineDao?.deleteChaptersForNovel(fromUrl)
            offlineDao?.deleteNovel(fromUrl)
            offlineDao?.deleteNovelDetails(fromUrl)
        }

        MigrateOutcome.Moved(
            MovedInfo(
                position = position,
                readMarks = readMarks.size,
                historyMoved = historyMoves
            )
        )
    }
}
