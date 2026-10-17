package com.safemode.hbconnect.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.ApiKeyCreatedOut
import com.safemode.hbconnect.data.remote.models.ApiKeyOut
import com.safemode.hbconnect.data.remote.models.UserOut
import com.safemode.hbconnect.data.settings.AuthMode
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the Account screen: the signed-in user's profile plus the personal API keys for their
 * account. Profile edits, password changes, key management and "log out everywhere" all run here.
 */
class AccountViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<UserOut>>(UiState.Loading)
    val state: StateFlow<UiState<UserOut>> = _state.asStateFlow()

    private val _apiKeys = MutableStateFlow<List<ApiKeyOut>>(emptyList())
    val apiKeys: StateFlow<List<ApiKeyOut>> = _apiKeys.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

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

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.self().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to load your account.") },
        )
        if (_state.value is UiState.Success) {
            _apiKeys.value = repository.getApiKeys().getOrDefault(emptyList())
        }
    }

    fun updateProfile(name: String, email: String, onResult: (Boolean, String?) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.updateSelf(name.trim(), email.trim())
            if (result.isSuccess) reload(showLoading = false)
            _busy.value = false
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun changePassword(current: String, new: String, onResult: (Boolean, String?) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.changePassword(current, new)
            _busy.value = false
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun createApiKey(name: String, expiresAt: String?, onResult: (ApiKeyCreatedOut?, String?) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.createApiKey(name.trim(), expiresAt)
            if (result.isSuccess) _apiKeys.value = repository.getApiKeys().getOrDefault(_apiKeys.value)
            _busy.value = false
            result.fold(
                onSuccess = { onResult(it, null) },
                onFailure = { onResult(null, it.message) },
            )
        }
    }

    fun deleteApiKey(id: String, onResult: (Boolean, String?) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.deleteApiKey(id)
            if (result.isSuccess) _apiKeys.value = repository.getApiKeys().getOrDefault(_apiKeys.value)
            _busy.value = false
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    /**
     * Invalidates every login session for this user. When the app is connected with username/
     * password, the current session's token is revoked too, so the local session is cleared and the
     * screen returns to a disconnected state. API-key connections keep working.
     */
    fun logoutEverywhere(onResult: (Boolean, String?, Boolean) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.logoutAll()
            val config = settings.config.first()
            val clearedLocal = result.isSuccess && config.authMode == AuthMode.LOGIN
            if (clearedLocal) {
                val cleared = config.copy(
                    token = "",
                    tokenExpiresAt = "",
                    username = "",
                    activeGroupId = "",
                )
                settings.save(cleared)
                Graph.apiProvider.updateConfig(cleared)
                Graph.serverStatus.reportChecking()
                _state.value = UiState.NotConfigured
            }
            _busy.value = false
            onResult(result.isSuccess, result.exceptionOrNull()?.message, clearedLocal)
        }
    }
}
