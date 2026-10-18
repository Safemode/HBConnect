package com.safemode.hbconnect.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.safemode.hbconnect.MainActivity

/**
 * Posts a single notification when the offline upload queue finishes draining. This only ever fires
 * for a queue that filled up while offline (ordinary online writes never enter the queue), which is
 * why there is no "everything was already online" case to suppress.
 */
class QueueNotifier(private val context: Context) {

    /** Whether notifications can be shown (channel enabled, and the runtime permission on 13+). */
    fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /** Announces the result of a drain: how many queued changes synced and how many failed. */
    fun notifyDrainComplete(uploaded: Int, failed: Int) {
        if (!canNotify()) return
        if (uploaded == 0 && failed == 0) return

        val title = if (failed == 0) "Queued uploads complete" else "Queued uploads finished with errors"
        val text = buildString {
            if (uploaded > 0) append("$uploaded queued ${plural(uploaded, "change", "changes")} uploaded")
            if (failed > 0) {
                if (uploaded > 0) append("; ")
                append("$failed failed - open the queue to retry")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "upload_queue"
        private const val NOTIFICATION_ID = 424242

        /** Creates the upload-queue notification channel (no-op below API 26). */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Upload queue",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Tells you when changes saved offline have finished uploading."
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
