package com.emptycastle.novery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.emptycastle.novery.data.repository.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Slice-05.2: handles notification actions for library updates.
 * Mark-as-read acknowledges the badge + inbox entry and dismisses the
 * notification. Never throws out of onReceive.
 */
class UpdateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != LibraryUpdateNotifier.ACTION_MARK_READ) return

        val novelUrl = intent.getStringExtra(LibraryUpdateNotifier.EXTRA_NOVEL_URL)
        if (novelUrl.isNullOrBlank()) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                RepositoryProvider.getNotificationRepository().markAsSeen(novelUrl)
                RepositoryProvider.getLibraryRepository().acknowledgeNewChapters(novelUrl)
                NotificationManagerCompat.from(context.applicationContext).cancel(
                    LibraryUpdateNotifier.notificationIdFor(novelUrl)
                )
                Log.i(TAG, "Marked update as read: $novelUrl")
            } catch (e: Exception) {
                Log.w(TAG, "Mark-read action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UpdateActionReceiver"
    }
}
