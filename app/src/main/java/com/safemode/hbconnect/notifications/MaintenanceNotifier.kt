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
import com.safemode.hbconnect.R
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Builds and posts "maintenance due" reminder notifications. Each due entry gets its own
 * notification, bundled under a group; tapping one opens the item it belongs to.
 */
class MaintenanceNotifier(private val context: Context) {

    /** Whether notifications can actually be shown (channel enabled, and runtime permission on 13+). */
    fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /** Posts a reminder for one scheduled maintenance entry. Silently ignored if posting is blocked. */
    fun notifyDue(entry: MaintenanceEntry, due: LocalDate, today: LocalDate, notificationId: Int) {
        if (!canNotify()) return

        val title = entry.name?.takeIf { it.isNotBlank() } ?: "Maintenance due"
        val item = entry.itemName?.takeIf { it.isNotBlank() }
        val text = buildString {
            if (item != null) append(item).append(" • ")
            append(duePhrase(due, today))
        }

        val pending = contentIntent(entry.itemID, notificationId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_maintenance_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun duePhrase(due: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days < 0 -> "Overdue since ${due}"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            else -> "Due in $days days"
        }
    }

    /** Opens the item's detail when the item id is known, otherwise just launches the app. */
    private fun contentIntent(entityId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!entityId.isNullOrBlank()) putExtra(MainActivity.EXTRA_ENTITY_ID, entityId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "maintenance_reminders"
        private const val GROUP_KEY = "com.safemode.hbconnect.MAINTENANCE"

        /** Creates the maintenance-reminders notification channel (no-op below API 26). */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Maintenance reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders for scheduled maintenance that is due."
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
