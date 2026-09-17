package com.emptycastle.novery.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.emptycastle.novery.MainActivity
import com.emptycastle.novery.data.repository.LibraryItem
import com.emptycastle.novery.util.NotificationImageLoader

/**
 * Slice-05.2: system notifications for background library updates.
 *
 * Posts a summary plus per-novel cover notifications (BigPictureStyle)
 * with Open (deep link to details) and Mark-as-read actions.
 * Never throws: permission-gated and fully guarded so a notification
 * failure can never fail the update worker.
 */
object LibraryUpdateNotifier {

    private const val TAG = "LibraryUpdateNotifier"

    const val CHANNEL_UPDATES = "novery_library_updates_channel"

    const val ACTION_OPEN_NOVEL = "com.emptycastle.novery.action.OPEN_NOVEL"
    const val ACTION_MARK_READ = "com.emptycastle.novery.action.UPDATE_MARK_READ"

    const val EXTRA_NOVEL_URL = "extra_novel_url"
    const val EXTRA_PROVIDER_NAME = "extra_provider_name"

    const val NOTIFICATION_ID_SUMMARY = 6000
    private const val NOTIFICATION_ID_NOVEL_BASE = 6001
    private const val MAX_PER_NOVEL_NOTIFICATIONS = 5

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_UPDATES) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_UPDATES,
                    "Library Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when new chapters are found"
                    setShowBadge(true)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "ensureChannel failed", e)
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return try {
            NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * @param updatedItems library items currently showing new chapters.
     */
    suspend fun notifyUpdates(context: Context, updatedItems: List<LibraryItem>) {
        try {
            if (updatedItems.isEmpty()) return
            if (!areNotificationsEnabled(context)) {
                Log.i(TAG, "Notifications disabled — skipping update notification")
                return
            }
            ensureChannel(context)

            val totalNew = updatedItems.sumOf { it.newChapterCount.coerceAtLeast(0) }
            postSummary(context, updatedItems.size, totalNew)

            updatedItems.take(MAX_PER_NOVEL_NOTIFICATIONS).forEach { item ->
                try {
                    postNovel(context, item)
                } catch (e: Exception) {
                    Log.w(TAG, "Per-novel notification failed for ${item.novel.url}", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "notifyUpdates failed", e)
        }
    }

    private suspend fun postSummary(context: Context, novelCount: Int, totalNew: Int) {
        val appContext = context.applicationContext
        val text = if (novelCount == 1) {
            "$totalNew new chapter${if (totalNew == 1) "" else "s"} found"
        } else {
            "$totalNew new chapters across $novelCount novels"
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Library updates")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(appContext, 0))
            .build()
        NotificationManagerCompat.from(appContext)
            .notify(NOTIFICATION_ID_SUMMARY, notification)
        Log.i(TAG, "Posted update summary: $text")
    }

    private suspend fun postNovel(context: Context, item: LibraryItem) {
        val appContext = context.applicationContext
        val novelUrl = item.novel.url
        val notificationId = NOTIFICATION_ID_NOVEL_BASE +
            (novelUrl.hashCode() and 0x7FFFFFFF) % 900

        val cover: Bitmap? = try {
            NotificationImageLoader.loadImage(appContext, item.novel.posterUrl, rounded = false)
        } catch (_: Exception) {
            null
        }

        val newCount = item.newChapterCount.coerceAtLeast(0)
        val builder = NotificationCompat.Builder(appContext, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(item.novel.name)
            .setContentText(
                "$newCount new chapter${if (newCount == 1) "" else "s"} on ${item.novel.apiName}"
            )
            .setAutoCancel(true)
            .setContentIntent(openNovelIntent(appContext, novelUrl, item.novel.apiName))
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open",
                openNovelIntent(appContext, novelUrl, item.novel.apiName)
            )
            .addAction(
                android.R.drawable.ic_menu_agenda,
                "Mark read",
                markReadIntent(appContext, novelUrl)
            )
        if (cover != null) {
            builder.setLargeIcon(cover)
            builder.setStyle(
                androidx.core.app.NotificationCompat.BigPictureStyle()
                    .bigPicture(cover)
                    .setSummaryText("${item.novel.apiName} • $newCount new")
            )
        }
        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun openNovelIntent(context: Context, novelUrl: String, providerName: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOVEL
            putExtra(EXTRA_NOVEL_URL, novelUrl)
            putExtra(EXTRA_PROVIDER_NAME, providerName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = 7000 + (novelUrl.hashCode() and 0x7FFFFFFF) % 900
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun markReadIntent(context: Context, novelUrl: String): PendingIntent {
        val intent = Intent(ACTION_MARK_READ).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_NOVEL_URL, novelUrl)
        }
        val requestCode = 8000 + (novelUrl.hashCode() and 0x7FFFFFFF) % 900
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notificationIdFor(novelUrl: String): Int {
        return NOTIFICATION_ID_NOVEL_BASE + (novelUrl.hashCode() and 0x7FFFFFFF) % 900
    }
}
