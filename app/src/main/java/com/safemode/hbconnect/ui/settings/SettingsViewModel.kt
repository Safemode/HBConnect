package com.safemode.hbconnect.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.settings.ConnectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the Settings screen — standalone app preferences (theme, item list order) and cache
 * management. Each change is persisted immediately; connection details live on the Server screen.
 */
class SettingsViewModel : ViewModel() {
    private val settings = Graph.settingsRepository
    private val apiProvider = Graph.apiProvider

    private val _config = MutableStateFlow(ConnectionConfig())
    val config: StateFlow<ConnectionConfig> = _config.asStateFlow()

    private val _cacheUsage = MutableStateFlow(0L)
    val cacheUsage: StateFlow<Long> = _cacheUsage.asStateFlow()

    init {
        viewModelScope.launch { _config.value = settings.config.first() }
        refreshCacheUsage()
    }

    fun refreshCacheUsage() {
        viewModelScope.launch {
            _cacheUsage.value = withContext(Dispatchers.IO) { apiProvider.cacheUsageBytes() }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { apiProvider.clearCache() }
            _cacheUsage.value = withContext(Dispatchers.IO) { apiProvider.cacheUsageBytes() }
        }
    }

    /** Applies a preference change and persists it right away (connection fields untouched). */
    fun updateAndSave(transform: (ConnectionConfig) -> ConnectionConfig) {
        val updated = transform(_config.value)
        _config.value = updated
        viewModelScope.launch {
            val merged = settings.config.first().copy(
                themePreference = updated.themePreference,
                cacheSize = updated.cacheSize,
                itemSortOrder = updated.itemSortOrder,
                showArchived = updated.showArchived,
            )
            settings.save(merged)
            apiProvider.updateConfig(merged)
        }
    }
}
