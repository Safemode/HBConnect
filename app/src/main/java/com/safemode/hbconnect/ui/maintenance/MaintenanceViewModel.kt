package com.safemode.hbconnect.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import com.safemode.hbconnect.data.remote.models.MaintenanceEntryRequest
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MaintenanceViewModel(private val entityId: String) : ViewModel() {
    private val repository = Graph.repository

    private val _state = MutableStateFlow<UiState<List<MaintenanceEntry>>>(UiState.Loading)
    val state: StateFlow<UiState<List<MaintenanceEntry>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load(showLoading = true)
    }

    fun load(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState.Loading
            _state.value = repository.getMaintenance(entityId).fold(
                onSuccess = { list -> UiState.Success(list.sortedByDescending { sortKey(it) }) },
                onFailure = { UiState.Error(it.message ?: "Failed to load maintenance.") },
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            load(showLoading = false)
            _refreshing.value = false
        }
    }

    fun create(body: MaintenanceEntryRequest, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.createMaintenance(entityId, body).fold(
                onSuccess = { load(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    fun update(id: String, body: MaintenanceEntryRequest, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.updateMaintenance(id, body).fold(
                onSuccess = { load(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    fun delete(id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteMaintenance(id).fold(
                onSuccess = { load(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    /** Sort newest-activity first: completed by completion date, else by scheduled date. */
    private fun sortKey(e: MaintenanceEntry): String =
        (e.completedDate?.takeIf { it.isNotBlank() } ?: e.scheduledDate.orEmpty())
}
