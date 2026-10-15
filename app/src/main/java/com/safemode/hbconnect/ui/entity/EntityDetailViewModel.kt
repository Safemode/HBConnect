package com.safemode.hbconnect.ui.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EntityDetailViewModel(private val entityId: String) : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<EntityOut>>(UiState.Loading)
    val state: StateFlow<UiState<EntityOut>> = _state.asStateFlow()

    /** For locations: the entities contained directly within. */
    private val _children = MutableStateFlow<List<EntitySummary>>(emptyList())
    val children: StateFlow<List<EntitySummary>> = _children.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        load(showLoading = true)
    }

    fun load(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState.Loading
            val result = repository.getEntity(entityId)
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Failed to load.") },
            )
            val entity = result.getOrNull()
            if (entity?.isLocation == true) {
                val includeArchived = settings.config.first().showArchived
                _children.value = repository.getChildren(entityId, includeArchived).getOrDefault(emptyList())
            } else {
                _children.value = emptyList()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            load(showLoading = false)
            _refreshing.value = false
        }
    }

    fun delete(onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteEntity(entityId).fold(
                onSuccess = {
                    _deleted.value = true
                    onDone(true, null)
                },
                onFailure = { onDone(false, it.message) },
            )
        }
    }
}
