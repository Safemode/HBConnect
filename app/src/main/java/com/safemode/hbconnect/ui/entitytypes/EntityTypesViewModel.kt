package com.safemode.hbconnect.ui.entitytypes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityTypeCreate
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.data.remote.models.EntityTypeUpdate
import com.safemode.hbconnect.data.queue.OpKind
import com.safemode.hbconnect.data.queue.submitWrite
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the Entity types screen: lists the group's entity types and creates/updates/deletes them.
 *
 * Follows the list-screen pattern used across the app: [state] holds the one-shot load result
 * (loading/success/error/not-configured) and [refreshing] drives the pull-to-refresh spinner.
 * [load] shows the full-screen spinner; [reloadSilently] refetches without it (used for background
 * refreshes so the list doesn't flash). A reachability callback silently reloads when the server
 * comes back, so a screen opened while offline fills in once the connection returns.
 */
class EntityTypesViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<EntityTypeSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<EntityTypeSummary>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
        refreshWhenServerReachable { reloadSilently() }
    }

    fun load() {
        viewModelScope.launch { reload(showLoading = true) }
    }

    fun reloadSilently() {
        viewModelScope.launch { reload(showLoading = false) }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(showLoading = false)
            _refreshing.value = false
        }
    }

    fun create(name: String, icon: String, isLocation: Boolean, onResult: (Boolean, String?) -> Unit) {
        val body = EntityTypeCreate(
            name = name.trim(),
            icon = icon.trim().ifBlank { null },
            isLocation = isLocation,
        )
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.CREATE_TYPE,
                payload = body,
                label = "New type: ${body.name}",
                direct = { repository.createEntityType(body).map {} },
                onSent = { reloadSilently() },
                onResult = onResult,
            )
        }
    }

    fun update(id: String, name: String, icon: String, isLocation: Boolean, onResult: (Boolean, String?) -> Unit) {
        val body = EntityTypeUpdate(
            name = name.trim(),
            icon = icon.trim().ifBlank { null },
            isLocation = isLocation,
        )
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.UPDATE_TYPE,
                payload = body,
                targetId = id,
                label = "Edit type: ${body.name}",
                direct = { repository.updateEntityType(id, body).map {} },
                onSent = { reloadSilently() },
                onResult = onResult,
            )
        }
    }

    fun delete(id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.DELETE_TYPE,
                payload = null,
                targetId = id,
                label = "Delete type",
                direct = { repository.deleteEntityType(id) },
                onSent = { reloadSilently() },
                onResult = onResult,
            )
        }
    }

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.getEntityTypes().fold(
            onSuccess = { types -> UiState.Success(types.sortedBy { it.name?.lowercase().orEmpty() }) },
            onFailure = { UiState.Error(it.message ?: "Failed to load entity types.") },
        )
    }
}
