package com.safemode.hbconnect.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An in-app diagnostic log of HBConnect's own activity (chiefly the HTTP requests it makes and their
 * outcomes). Homebox does not expose its server logs over the API, so this is the client's own log,
 * useful for troubleshooting connectivity. Entries are held in a bounded in-memory ring buffer and
 * are lost when the process ends.
 */
object DiagnosticLog {

    enum class Level { DBG, INF, WARN, ERR }

    data class Entry(val timeMillis: Long, val level: Level, val message: String)

    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun log(level: Level, message: String) {
        val appended = _entries.value + Entry(System.currentTimeMillis(), level, message)
        _entries.value = if (appended.size > MAX_ENTRIES) appended.takeLast(MAX_ENTRIES) else appended
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
