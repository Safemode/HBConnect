package com.safemode.hbconnect.ui.currency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the Currency screen: lists the server's supported currencies and sets the active
 * collection's currency (owner-only, PUT /groups). The group's name is preserved on update, and
 * [Graph.loadCurrency] is refreshed afterward so money formatting updates app-wide.
 */
class CurrencyViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<Currency>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Currency>>> = _state.asStateFlow()

    /** The group's current currency code, e.g. "USD". */
    private val _activeCode = MutableStateFlow<String?>(null)
    val activeCode: StateFlow<String?> = _activeCode.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // Preserved and re-sent on update so changing the currency doesn't blank the group name.
    private var groupName: String = ""

    init {
        load()
        refreshWhenServerReachable { load() }
    }

    fun load() {
        viewModelScope.launch { reload() }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload()
            _refreshing.value = false
        }
    }

    private suspend fun reload() {
        val config = settings.config.first()
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        repository.group().getOrNull()?.let {
            groupName = it.name.orEmpty()
            _activeCode.value = it.currency
        }
        _state.value = repository.currencies().fold(
            onSuccess = { list -> UiState.Success(list.sortedBy { it.code?.uppercase() }) },
            onFailure = { UiState.Error(it.message ?: "Failed to load currencies.") },
        )
    }

    /** Sets the group's currency to [code]; refreshes app-wide formatting on success. */
    fun select(code: String, onResult: (Boolean, String?) -> Unit) {
        if (_busy.value || code == _activeCode.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.updateGroup(name = groupName, currency = code)
            result.onSuccess {
                groupName = it.name ?: groupName
                _activeCode.value = it.currency ?: code
                Graph.loadCurrency()
            }
            _busy.value = false
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
