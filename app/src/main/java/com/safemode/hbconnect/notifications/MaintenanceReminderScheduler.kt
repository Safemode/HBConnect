package com.safemode.hbconnect.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Enqueues and cancels the periodic maintenance-reminder check. */
object MaintenanceReminderScheduler {

    private const val WORK_NAME = "maintenance-reminders"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Registers the periodic check when [enabled], or cancels it when not. Safe to call repeatedly. */
    fun update(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<MaintenanceReminderWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        // UPDATE keeps the existing schedule's timing but applies any worker/constraint changes.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Runs the check once, now — used right after the user enables reminders for immediate feedback. */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<MaintenanceReminderWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
