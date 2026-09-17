package com.emptycastle.novery.data.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.emptycastle.novery.data.local.entity.UpdateDetectionEntity
import com.emptycastle.novery.data.repository.LibraryItem
import com.emptycastle.novery.data.repository.LibraryRefreshResult
import com.emptycastle.novery.data.repository.RepositoryProvider
import com.emptycastle.novery.service.LibraryUpdateNotifier

/**
 * Slice-01: scheduled library update worker.
 *
 * 1.1: skeleton (log + success) to verify scheduling plumbing.
 * 1.3: real refresh — smart-skips completed/dropped/unstarted/fully-read
 * novels, reloads details for the rest via [LibraryRepository], and syncs
 * the notification inbox for novels with new chapters.
 */
class LibraryUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Slice-05.4: visible in the in-app banner while running.
        BackgroundWorkTracker.start(
            BackgroundWorkTracker.ID_LIBRARY_UPDATE,
            "Checking library for new chapters"
        )
        return try {
            val libraryRepository = RepositoryProvider.getLibraryRepository()
            val novelRepository = RepositoryProvider.getNovelRepository()
            val notificationRepository = RepositoryProvider.getNotificationRepository()

            val items = libraryRepository.getLibrary()
            if (items.isEmpty()) {
                Log.i(TAG, "LibraryUpdate: library empty, nothing to check")
                return Result.success(workDataOf(KEY_CHECKED to 0, KEY_NEW to 0))
            }

            val (skipped, eligible) = items.partition { item ->
                LibraryUpdateFilter.shouldSkipNovel(
                    item.readingStatus,
                    item.unreadChapterCount,
                    item.totalChapterCount,
                    hasStartedReading = item.lastReadPosition != null
                ) != null
            }
            if (skipped.isNotEmpty()) {
                Log.i(
                    TAG,
                    "LibraryUpdate: skipping ${skipped.size}/${items.size} " +
                        "(completed/dropped/unstarted/fully-read)"
                )
            }

            val result: LibraryRefreshResult = if (eligible.isEmpty()) {
                LibraryRefreshResult(
                    updatedCount = 0,
                    totalNewChapters = 0,
                    totalChecked = 0,
                    skippedCount = items.size
                )
            } else {
                val beforeCounts = items.associate { it.novel.url to it.newChapterCount }
                val refresh = libraryRepository.refreshNovelsByUrls(
                    getProvider = { name -> novelRepository.getProvider(name) },
                    novelUrls = eligible.map { it.novel.url }.toSet(),
                    onProgress = { current, total, name ->
                        Log.i(TAG, "LibraryUpdate: [$current/$total] $name")
                        BackgroundWorkTracker.update(
                            BackgroundWorkTracker.ID_LIBRARY_UPDATE,
                            "$current/$total · $name"
                        )
                    }
                )
                recordDetections(beforeCounts)
                refresh
            }

            var notified = 0
            val withNew = libraryRepository.getLibrary().filter { it.hasNewChapters }
            withNew.forEach { item ->
                try {
                    notificationRepository.addOrUpdateNotification(
                        item.novel.url,
                        item.novel.apiName
                    )
                    notified++
                } catch (e: Exception) {
                    Log.w(TAG, "LibraryUpdate: notify failed for ${item.novel.url}", e)
                }
            }

            // Slice-05.2: system notification on findings. Manual runs
            // notify too — the user asked to check, and the summary is
            // the result delivery.
            if (result.totalNewChapters > 0) {
                notifySystem(withNew)
            }

            // Slice-05.3: persist this run's failures for the error screen
            // (replaces the previous snapshot, even when empty).
            saveErrors(result.errorDetails)

            Log.i(
                TAG,
                "LibraryUpdate complete: checked=${result.totalChecked} " +
                    "skipped=${result.skippedCount} updated=${result.updatedCount} " +
                    "newChapters=${result.totalNewChapters} notified=$notified"
            )
            Result.success(
                workDataOf(
                    KEY_CHECKED to result.totalChecked,
                    KEY_NEW to result.totalNewChapters
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "LibraryUpdate failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            BackgroundWorkTracker.finish(BackgroundWorkTracker.ID_LIBRARY_UPDATE)
        }
    }

    /**
     * Slice-05.2: posts the system notification. Isolated so notification
     * failures can never fail the worker.
     */
    private suspend fun notifySystem(withNew: List<LibraryItem>) {
        try {
            LibraryUpdateNotifier.notifyUpdates(applicationContext, withNew)
        } catch (e: Exception) {
            Log.w(TAG, "LibraryUpdate: system notification failed", e)
        }
    }

    /**
     * Slice-05.3: snapshots this run's failures. Isolated like notifySystem.
     */
    private suspend fun saveErrors(
        errorDetails: List<com.emptycastle.novery.data.repository.RefreshError>
    ) {
        try {
            RepositoryProvider.getUpdateErrorStore().replaceAll(
                errorDetails.map { detail ->
                    UpdateError(
                        novelUrl = detail.novelUrl,
                        novelName = detail.novelName,
                        providerName = detail.providerName,
                        message = detail.message
                    )
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "LibraryUpdate: error snapshot failed", e)
        }
    }

    /**
     * Slice-01.4: compare unacknowledged new-chapter counts before/after the
     * refresh and record a detection event for each novel that gained
     * chapters. Feeds the update-interval predictor.
     */
    private suspend fun recordDetections(beforeCounts: Map<String, Int>) {
        try {
            val dao = RepositoryProvider.getDatabase().updateHistoryDao()
            val after = RepositoryProvider.getLibraryRepository().getLibrary()
            var recorded = 0
            for (item in after) {
                val before = beforeCounts[item.novel.url] ?: 0
                val gained = (item.newChapterCount - before).coerceAtLeast(0)
                if (gained > 0) {
                    dao.insert(
                        UpdateDetectionEntity(
                            novelUrl = item.novel.url,
                            detectedAt = System.currentTimeMillis(),
                            newChapters = gained
                        )
                    )
                    dao.prune(item.novel.url, keep = 10)
                    recorded++
                }
            }
            if (recorded > 0) Log.i(TAG, "LibraryUpdate: recorded $recorded detection(s)")
        } catch (e: Exception) {
            Log.w(TAG, "LibraryUpdate: detection recording failed", e)
        }
    }

    companion object {
        const val TAG = "LibraryUpdateWorker"
        const val KEY_CHECKED = "checked"
        const val KEY_NEW = "new_chapters"
    }
}
