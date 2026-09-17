package com.emptycastle.novery.data.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.emptycastle.novery.data.repository.LibraryRefreshResult
import com.emptycastle.novery.data.repository.RepositoryProvider

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
                libraryRepository.refreshNovelsByUrls(
                    getProvider = { name -> novelRepository.getProvider(name) },
                    novelUrls = eligible.map { it.novel.url }.toSet(),
                    onProgress = { current, total, name ->
                        Log.i(TAG, "LibraryUpdate: [$current/$total] $name")
                    }
                )
            }

            var notified = 0
            libraryRepository.getLibrary()
                .filter { it.hasNewChapters }
                .forEach { item ->
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
        }
    }

    companion object {
        const val TAG = "LibraryUpdateWorker"
        const val KEY_CHECKED = "checked"
        const val KEY_NEW = "new_chapters"
    }
}
