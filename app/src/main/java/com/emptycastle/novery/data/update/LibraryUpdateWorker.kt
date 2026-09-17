package com.emptycastle.novery.data.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Slice-01: scheduled library update worker.
 *
 * Sub-item 1.1 is the skeleton behind the [LibraryUpdateScheduler] feature
 * flag: it only logs and succeeds so scheduling plumbing can be verified
 * with zero production impact. The real refresh lands in 1.3.
 */
class LibraryUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "LibraryUpdate tick (skeleton): enabled=${inputData.getBoolean(KEY_ENABLED_SNAPSHOT, false)}")
            // TODO(slice-01.3): run the actual library refresh here.
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "LibraryUpdate tick failed", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "LibraryUpdateWorker"
        const val KEY_ENABLED_SNAPSHOT = "enabled_snapshot"
    }
}
