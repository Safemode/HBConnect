package com.safemode.hbconnect.ui.entitytypes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityTypeCreate
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.data.remote.models.EntityTypeUpdate
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            repository.createEntityType(
                EntityTypeCreate(
                    name = name.trim(),
                    icon = icon.trim().ifBlank { null },
                    isLocation = isLocation,
                ),
            ).fold(
                onSuccess = { reloadSilently(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    fun update(id: String, name: String, icon: String, isLocation: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.updateEntityType(
                id,
                EntityTypeUpdate(
                    name = name.trim(),
                    icon = icon.trim().ifBlank { null },
                    isLocation = isLocation,
                ),
            ).fold(
                onSuccess = { reloadSilently(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    fun delete(id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteEntityType(id).fold(
                onSuccess = { reloadSilently(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
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
