package com.safemode.hbconnect.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.Group
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the Collections screen: lists the groups the user belongs to and switches the active one.
 * Switching just saves [com.safemode.hbconnect.data.settings.ConnectionConfig.activeGroupId]; Graph
 * reacts by clearing caches and refetching, and every request then carries the new X-Tenant header.
 */
class CollectionsViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<Group>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Group>>> = _state.asStateFlow()

    /** The id of the collection currently in effect (chosen, or the server default when none). */
    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    init {
        load()
        refreshWhenServerReachable { load() }
    }

    fun load() {
        viewModelScope.launch {
            val config = settings.config.first()
            if (!config.isConfigured) {
                _state.value = UiState.NotConfigured
                return@launch
            }
            // The default group (used when no override is set) is whatever GET /groups returns.
            val defaultId = repository.group().getOrNull()?.id
            _activeId.value = config.activeGroupId.ifBlank { defaultId }
            _state.value = repository.groupsAll().fold(
                onSuccess = { groups -> UiState.Success(groups.sortedBy { it.name?.lowercase() }) },
                onFailure = { UiState.Error(it.message ?: "Failed to load collections.") },
            )
        }
    }

    /** Switches the active collection. No-op if already active. */
    fun select(group: Group) {
        val id = group.id ?: return
        if (id == _activeId.value) return
        viewModelScope.launch {
            _activeId.value = id
            val config = settings.config.first()
            settings.save(config.copy(activeGroupId = id))
        }
    }
}
