package com.safemode.hbconnect.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safemode.hbconnect.Graph
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Periodically checks the group's scheduled maintenance and posts a reminder for anything due within
 * the user's chosen lead time (including overdue). De-duplicated via [MaintenanceReminderStore] so a
 * given entry is announced once per scheduled date. A no-op when reminders are off, the app isn't
 * configured, or notifications aren't permitted; a network failure returns [Result.retry].
 */
class MaintenanceReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val config = Graph.settingsRepository.config.first()
        if (!config.maintenanceReminders || !config.isConfigured) return Result.success()

        val notifier = MaintenanceNotifier(applicationContext)
        if (!notifier.canNotify()) return Result.success()

        Graph.apiProvider.updateConfig(config)
        val entries = Graph.repository.getAllMaintenance(SCHEDULED).getOrElse {
            return Result.retry()
        }

        val today = LocalDate.now()
        val cutoff = today.plusDays(config.maintenanceLead.days)
        val due = entries.mapNotNull { entry ->
            if (entry.isCompleted) return@mapNotNull null
            val date = parseDate(entry.scheduledDate) ?: return@mapNotNull null
            if (date.isAfter(cutoff)) null else entry to date
        }

        val store = MaintenanceReminderStore(applicationContext)
        val alreadyNotified = store.notifiedKeys()
        val currentKeys = mutableSetOf<String>()
        for ((entry, date) in due) {
            val key = "${entry.id}@${entry.scheduledDate}"
            currentKeys += key
            if (key !in alreadyNotified) {
                notifier.notifyDue(entry, date, today, notificationId = key.hashCode())
            }
        }
        // Keep only still-due keys: drops completed/removed entries so they can remind again later.
        store.setNotifiedKeys(currentKeys)
        return Result.success()
    }

    private fun parseDate(raw: String?): LocalDate? {
        val value = raw?.takeIf { it.isNotBlank() && !it.startsWith(EMPTY_DATE_PREFIX) } ?: return null
        return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }

    private companion object {
        const val SCHEDULED = "scheduled"
        // Homebox serializes an unset date as 0001-01-01T...; treat it as "no date".
        const val EMPTY_DATE_PREFIX = "0001-01-01"
    }
}
