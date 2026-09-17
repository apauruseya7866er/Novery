package com.emptycastle.novery.data.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit

/**
 * Slice-01: owns scheduling/cancelling of [LibraryUpdateWorker].
 *
 * The job only exists while the user enables it in Settings → Library
 * Updates (default OFF), so production behavior is unchanged until then.
 */
object LibraryUpdateScheduler {

    const val UNIQUE_WORK_NAME = "novery_library_update"
    const val WORK_TAG = "novery_library_update"
    private const val TAG = "LibraryUpdateScheduler"

    /** Supported intervals in hours (mirrors the Settings dropdown). */
    val SUPPORTED_INTERVALS_HOURS = listOf(12L, 24L, 48L, 72L, 168L)

    fun intervalLabel(hours: Long): String = when (hours) {
        12L -> "Every 12 hours"
        24L -> "Every 24 hours"
        48L -> "Every 2 days"
        72L -> "Every 3 days"
        168L -> "Every week"
        else -> "Every $hours hours"
    }

    /**
     * Applies the desired state: schedules (or re-schedules) when enabled,
     * cancels otherwise. Safe to call on every app start.
     */
    fun apply(
        context: Context,
        enabled: Boolean,
        intervalHours: Long,
        wifiOnly: Boolean = true,
        requireCharging: Boolean = false
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG, "Library updates disabled — cancelled $UNIQUE_WORK_NAME")
            return
        }
        val interval = intervalHours.coerceIn(
            SUPPORTED_INTERVALS_HOURS.min(),
            SUPPORTED_INTERVALS_HOURS.max()
        )
        val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(
            interval, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setConstraints(buildConstraints(wifiOnly, requireCharging))
            .addTag(WORK_TAG)
            .setInputData(workDataOf(LibraryUpdateWorker.KEY_ENABLED_SNAPSHOT to true))
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.i(TAG, "Library updates scheduled every ${interval}h (wifiOnly=$wifiOnly, charging=$requireCharging)")
    }

    /**
     * Pure mapping from user prefs to WorkManager constraints — unit-tested.
     */
    fun buildConstraints(wifiOnly: Boolean, requireCharging: Boolean): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(requireCharging)
            .setRequiresBatteryNotLow(true)
            .build()
    }

    /** Synchronous snapshot for verification/debugging (tests, logcat). */
    fun getScheduledInfo(context: Context): ListenableFuture<List<WorkInfo>> {
        return WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
    }
}
