package com.emptycastle.novery.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emptycastle.novery.data.repository.RepositoryProvider
import com.emptycastle.novery.data.update.BackgroundWorkTracker
import java.io.File

/**
 * Slice-06.2: scheduled auto-backup worker.
 *
 * Writes a v2 backup into app-private storage, prunes to the keep limit,
 * and records the run timestamp. Never throws out of doWork.
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        BackgroundWorkTracker.start(WORK_ID, "Backing up library")
        return try {
            val context = applicationContext
            val database = RepositoryProvider.getDatabase()
            val prefs = RepositoryProvider.getPreferencesManager()
            val manager = BackupManager(context, database, prefs)

            val json = manager.exportToJson()
            val dir = File(context.filesDir, BackupScheduler.BACKUP_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, manager.generateBackupFileName())
            file.writeText(json, Charsets.UTF_8)

            BackupScheduler.pruneBackups(dir)
            prefs.setBackupLastAutoAt(System.currentTimeMillis())

            Log.i(TAG, "Auto-backup saved: ${file.name} (${file.length()} bytes)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            BackgroundWorkTracker.finish(WORK_ID)
        }
    }

    companion object {
        const val TAG = "BackupWorker"
        const val WORK_ID = "auto_backup"
    }
}
