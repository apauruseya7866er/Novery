package com.emptycastle.novery.data.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Slice-06.2: owns scheduling of [BackupWorker].
 * Backups are local files: no network constraint, but requires battery
 * not low and storage not low. Disabled by default.
 */
object BackupScheduler {

    const val UNIQUE_WORK_NAME = "novery_auto_backup"
    const val UNIQUE_NOW_NAME = "novery_auto_backup_now"
    const val WORK_TAG = "novery_auto_backup"
    const val BACKUP_DIR = "backups"
    const val KEEP_AUTO_BACKUPS = 4

    private const val TAG = "BackupScheduler"

    val SUPPORTED_INTERVALS_HOURS = listOf(12L, 24L, 48L, 168L)

    fun intervalLabel(hours: Long): String = when (hours) {
        12L -> "Every 12 hours"
        24L -> "Every 24 hours"
        48L -> "Every 2 days"
        168L -> "Every week"
        else -> "Every $hours hours"
    }

    fun apply(context: Context, enabled: Boolean, intervalHours: Long) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG, "Auto-backup disabled")
            return
        }
        val interval = intervalHours.coerceIn(
            SUPPORTED_INTERVALS_HOURS.min(),
            SUPPORTED_INTERVALS_HOURS.max()
        )
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            interval, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.i(TAG, "Auto-backup scheduled every ${interval}h")
    }

    /** One-shot manual trigger ("Back up now"). */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_NOW_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        Log.i(TAG, "Auto-backup run-now enqueued")
    }

    /**
     * Keeps the newest backups. Pure filename selection is unit-tested
     * via [selectDeletions].
     */
    fun pruneBackups(dir: File) {
        try {
            if (!dir.exists()) return
            val files = dir.listFiles { f ->
                f.isFile && f.name.startsWith("novery_backup_")
            }?.toList() ?: return
            for (victim in selectDeletions(files.map { it.name }, KEEP_AUTO_BACKUPS)) {
                File(dir, victim).delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backup prune failed", e)
        }
    }

    /**
     * Pure: which filenames to delete to keep the newest [keep].
     * Lexicographic order works because names embed yyyy-MM-dd_HHmm.
     */
    fun selectDeletions(names: List<String>, keep: Int): List<String> {
        if (names.size <= keep) return emptyList()
        return names.sorted().dropLast(keep)
    }
}
