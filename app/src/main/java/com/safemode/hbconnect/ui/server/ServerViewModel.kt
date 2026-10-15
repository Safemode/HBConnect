package com.safemode.hbconnect.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.UserOut
import com.safemode.hbconnect.data.settings.AuthMode
import com.safemode.hbconnect.data.settings.ConnectionConfig
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Editable connection form. Credentials are entered here but never persisted in plaintext. */
data class ServerForm(
    val scheme: String = "https",
    val host: String = "",
    val port: String = "",
    val authMode: AuthMode = AuthMode.LOGIN,
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
) {
    fun baseConfig() = ConnectionConfig(scheme = scheme, host = host, port = port, authMode = authMode)

    val canSubmit: Boolean
        get() = host.isNotBlank() && when (authMode) {
            AuthMode.LOGIN -> username.isNotBlank() && password.isNotBlank()
            AuthMode.API_KEY -> apiKey.isNotBlank()
        }
}

data class ServerInfo(val user: UserOut?, val version: String?)

class ServerViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _form = MutableStateFlow(ServerForm())
    val form: StateFlow<ServerForm> = _form.asStateFlow()

    private val _state = MutableStateFlow<UiState<ServerInfo>>(UiState.NotConfigured)
    val state: StateFlow<UiState<ServerInfo>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            val config = settings.config.first()
            _form.value = ServerForm(
                scheme = config.scheme,
                host = config.host,
                port = config.port,
                authMode = config.authMode,
                username = config.username,
            )
            if (config.isConfigured) reloadFromSaved()
        }
    }

    fun update(transform: (ServerForm) -> ServerForm) {
        _form.value = transform(_form.value)
    }

    /** Re-validates the currently saved connection (e.g. Retry). */
    fun reloadFromSaved() {
        viewModelScope.launch {
            val config = settings.config.first()
            if (!config.isConfigured) {
                _state.value = UiState.NotConfigured
                return@launch
            }
            Graph.apiProvider.updateConfig(config)
            validate()
        }
    }

    /**
     * Persists the connection: for login mode, exchanges username/password for a token first; for
     * API-key mode, stores the key directly. Then validates against the server.
     */
    fun saveAndConnect() {
        val form = _form.value
        if (!form.canSubmit) return
        viewModelScope.launch {
            _busy.value = true
            _state.value = UiState.Loading
            try {
                val existing = settings.config.first()
                val config = when (form.authMode) {
                    AuthMode.API_KEY -> existing.copy(
                        scheme = form.scheme,
                        host = form.host,
                        port = form.port,
                        authMode = AuthMode.API_KEY,
                        token = form.apiKey.trim(),
                        tokenExpiresAt = "",
                        username = "",
                    )
                    AuthMode.LOGIN -> {
                        // Point the API provider at the host (no token yet) so login can be issued.
                        Graph.apiProvider.updateConfig(form.baseConfig())
                        val login = repository.login(form.username.trim(), form.password)
                        val token = login.getOrNull()?.token
                        if (login.isFailure || token.isNullOrBlank()) {
                            _state.value = UiState.Error(
                                login.exceptionOrNull()?.message ?: "Login failed. Check your username and password.",
                            )
                            _busy.value = false
                            return@launch
                        }
                        existing.copy(
                            scheme = form.scheme,
                            host = form.host,
                            port = form.port,
                            authMode = AuthMode.LOGIN,
                            token = token,
                            tokenExpiresAt = login.getOrNull()?.expiresAt.orEmpty(),
                            username = form.username.trim(),
                        )
                    }
                }
                settings.save(config)
                Graph.apiProvider.updateConfig(config)
                // Clear the password from the form once it's been exchanged for a token.
                _form.value = _form.value.copy(password = "")
                validate()
            } finally {
                _busy.value = false
            }
        }
    }

    /** Clears the stored session and returns to a disconnected state. */
    fun logout() {
        viewModelScope.launch {
            _busy.value = true
            repository.clearSession()
            val cleared = settings.config.first().copy(token = "", tokenExpiresAt = "", username = "")
            settings.save(cleared)
            Graph.apiProvider.updateConfig(cleared)
            Graph.serverStatus.reportChecking()
            _form.value = _form.value.copy(password = "", apiKey = "")
            _state.value = UiState.NotConfigured
            _busy.value = false
        }
    }

    private suspend fun validate() {
        val reachable = withContext(Dispatchers.IO) { Graph.apiProvider.probeReachable() }
        Graph.serverStatus.report(reachable)
        val who = repository.self()
        if (who.isFailure) {
            _state.value = UiState.Error(who.exceptionOrNull()?.message ?: "Failed to reach server.")
            return
        }
        val version = repository.serverVersion().getOrNull()
        Graph.loadCurrency()
        _state.value = UiState.Success(ServerInfo(user = who.getOrNull(), version = version))
    }
}
