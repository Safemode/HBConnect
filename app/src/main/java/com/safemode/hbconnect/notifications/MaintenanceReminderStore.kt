package com.safemode.hbconnect.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.maintenanceReminderDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "maintenance_reminders")

/**
 * Remembers which scheduled-maintenance entries have already been notified, so the periodic worker
 * doesn't re-notify the same one on every run. A key combines the entry id with its scheduled date,
 * so rescheduling an entry (a new date) produces a fresh reminder.
 */
class MaintenanceReminderStore(private val context: Context) {

    suspend fun notifiedKeys(): Set<String> =
        context.maintenanceReminderDataStore.data.first()[KEY] ?: emptySet()

    suspend fun setNotifiedKeys(keys: Set<String>) {
        context.maintenanceReminderDataStore.edit { it[KEY] = keys }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("notified_keys")
    }
}
