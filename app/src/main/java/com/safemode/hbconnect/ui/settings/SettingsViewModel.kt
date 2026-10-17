package com.safemode.hbconnect.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.settings.ConnectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the Settings screen - standalone app preferences (theme, accent, item list order) and cache
 * management. Each change is persisted immediately; connection details live on the Server screen.
 */
class SettingsViewModel : ViewModel() {
    private val settings = Graph.settingsRepository
    private val apiProvider = Graph.apiProvider

    /** Reflects the persisted config directly, so the UI always shows what is actually saved. */
    val config: StateFlow<ConnectionConfig> = settings.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionConfig())

    private val _cacheUsage = MutableStateFlow(0L)
    val cacheUsage: StateFlow<Long> = _cacheUsage.asStateFlow()

    init {
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

    /**
     * Applies a preference change and persists it right away. The transform runs on the freshly
     * read config so every app preference (including the accent) is saved, and the connection
     * fields are carried through untouched.
     */
    fun updateAndSave(transform: (ConnectionConfig) -> ConnectionConfig) {
        viewModelScope.launch {
            val merged = transform(settings.config.first())
            settings.save(merged)
            apiProvider.updateConfig(merged)
        }
    }
}
