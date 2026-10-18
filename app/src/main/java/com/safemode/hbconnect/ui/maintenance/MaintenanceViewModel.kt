package com.safemode.hbconnect.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import com.safemode.hbconnect.data.remote.models.MaintenanceEntryRequest
import com.safemode.hbconnect.data.queue.OpKind
import com.safemode.hbconnect.data.queue.submitWrite
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the per-item maintenance log for one entity ([entityId]): lists its upkeep entries and
 * creates/updates/deletes them. Entries are sorted newest-activity first (see [sortKey]). Every
 * mutation re-fetches the list via [load] so the UI reflects the server. (The group-wide view is
 * [com.safemode.hbconnect.ui.maintenance.AllMaintenanceViewModel].)
 */
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
            Graph.uploadQueue.submitWrite(
                kind = OpKind.CREATE_MAINTENANCE,
                payload = body,
                targetId = entityId,
                label = "New maintenance: ${body.name}",
                direct = { repository.createMaintenance(entityId, body) },
                onSent = { load() },
                onResult = onResult,
            )
        }
    }

    fun update(id: String, body: MaintenanceEntryRequest, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.UPDATE_MAINTENANCE,
                payload = body,
                targetId = id,
                label = "Edit maintenance: ${body.name}",
                direct = { repository.updateMaintenance(id, body) },
                onSent = { load() },
                onResult = onResult,
            )
        }
    }

    fun delete(id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.DELETE_MAINTENANCE,
                payload = null,
                targetId = id,
                label = "Delete maintenance",
                direct = { repository.deleteMaintenance(id) },
                onSent = { load() },
                onResult = onResult,
            )
        }
    }

    /** Sort newest-activity first: completed by completion date, else by scheduled date. */
    private fun sortKey(e: MaintenanceEntry): String =
        (e.completedDate?.takeIf { it.isNotBlank() } ?: e.scheduledDate.orEmpty())
}
