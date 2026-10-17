package com.safemode.hbconnect.ui.value

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.ValueInsights
import com.safemode.hbconnect.data.remote.models.ValueOverTime
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.data.remote.LiveEvent
import com.safemode.hbconnect.ui.common.refreshOnLiveEvent
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Window options for the value-over-time chart. A null [days] means all of the group's history. */
enum class ValueRange(val label: String, val days: Long?) {
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365),
    ALL("All", null),
}

/**
 * Backs the Value breakdown screen (under Tools). Holds two independent pieces: [state] is the
 * in-app-computed [ValueInsights] (totals by location/tag/manufacturer/type, most expensive,
 * insured vs uninsured), and [valueOverTime] is the server's cumulative purchase-price series for
 * the chart. The chart has its own [range] window (6M/1Y/All); changing it refetches only the
 * series via [setRange], not the whole breakdown. Refreshes silently on reconnect and on a live
 * ENTITY event so the figures track changes made elsewhere.
 */
class ValueBreakdownViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<ValueInsights>>(UiState.Loading)
    val state: StateFlow<UiState<ValueInsights>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _range = MutableStateFlow(ValueRange.ONE_YEAR)
    val range: StateFlow<ValueRange> = _range.asStateFlow()

    private val _valueOverTime = MutableStateFlow<ValueOverTime?>(null)
    val valueOverTime: StateFlow<ValueOverTime?> = _valueOverTime.asStateFlow()

    init {
        load()
        refreshWhenServerReachable { reloadSilently() }
        refreshOnLiveEvent(LiveEvent.ENTITY) { reloadSilently() }
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

    /** Switches the chart window and refetches just the value-over-time series. */
    fun setRange(range: ValueRange) {
        if (range == _range.value) return
        _range.value = range
        viewModelScope.launch { loadValueOverTime() }
    }

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.valueInsights().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to compute value breakdown.") },
        )
        loadValueOverTime()
    }

    private suspend fun loadValueOverTime() {
        val days = _range.value.days
        val (start, end) = if (days == null) {
            null to null
        } else {
            val today = LocalDate.now()
            today.minusDays(days).toString() to today.toString()
        }
        _valueOverTime.value = repository.statsValueOverTime(start, end).getOrNull()
    }
}
