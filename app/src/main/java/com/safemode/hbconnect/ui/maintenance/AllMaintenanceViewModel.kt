package com.safemode.hbconnect.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Which maintenance entries the group-wide list shows; [status] is the API query value. */
enum class MaintenanceScope(val label: String, val status: String) {
    SCHEDULED("Scheduled", "scheduled"),
    COMPLETED("Completed", "completed"),
    ALL("All", "both"),
}

/** Backs the group-wide maintenance screen (GET /maintenance). */
class AllMaintenanceViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<MaintenanceEntry>>>(UiState.Loading)
    val state: StateFlow<UiState<List<MaintenanceEntry>>> = _state.asStateFlow()

    private val _scope = MutableStateFlow(MaintenanceScope.SCHEDULED)
    val scope: StateFlow<MaintenanceScope> = _scope.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // The running load, so a new one replaces it: a slower, older response can't land after a newer
    // one, and refresh() can wait for its load to finish before hiding the spinner.
    private var loadJob: Job? = null

    init {
        load(showLoading = true)
        refreshWhenServerReachable { load(showLoading = false) }
    }

    fun setScope(value: MaintenanceScope) {
        if (_scope.value == value) return
        _scope.value = value
        load(showLoading = true)
    }

    fun load(showLoading: Boolean = false): Job {
        loadJob?.cancel()
        return viewModelScope.launch {
            val config = settings.config.first()
            if (!config.isConfigured) {
                _state.value = UiState.NotConfigured
                return@launch
            }
            if (showLoading) _state.value = UiState.Loading
            _state.value = repository.getAllMaintenance(_scope.value.status).fold(
                onSuccess = { UiState.Success(sortEntries(it)) },
                onFailure = { UiState.Error(it.message ?: "Failed to load maintenance.") },
            )
        }.also { loadJob = it }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            load(showLoading = false).join()
            _refreshing.value = false
        }
    }

    /** Completed → most recent first; otherwise soonest scheduled first (undated last). */
    private fun sortEntries(list: List<MaintenanceEntry>): List<MaintenanceEntry> =
        if (_scope.value == MaintenanceScope.COMPLETED) {
            list.sortedByDescending { it.completedDate.orEmpty() }
        } else {
            list.sortedBy { it.scheduledDate?.takeIf { d -> d.isNotBlank() } ?: "9999-12-31" }
        }
}
