package com.safemode.hbconnect.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.UserOut
import com.safemode.hbconnect.data.settings.AuthMode
import com.safemode.hbconnect.data.settings.ConnectionConfig
import com.safemode.hbconnect.data.settings.ServerProfile
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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

    /** True while the form is drafting a brand-new server (not editing an existing one). */
    private val _addingNew = MutableStateFlow(false)
    val addingNew: StateFlow<Boolean> = _addingNew.asStateFlow()

    val servers: StateFlow<List<ServerProfile>> = settings.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeServerId: StateFlow<String> = settings.activeServerId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        viewModelScope.launch {
            val config = settings.config.first()
            loadFormFrom(config)
            if (config.isConfigured) reloadFromSaved()
        }
    }

    fun update(transform: (ServerForm) -> ServerForm) {
        _form.value = transform(_form.value)
    }

    private fun loadFormFrom(config: ConnectionConfig) {
        _form.value = ServerForm(
            scheme = config.scheme,
            host = config.host,
            port = config.port,
            authMode = config.authMode,
            username = config.username,
            apiKey = if (config.authMode == AuthMode.API_KEY) config.token else "",
        )
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

    /** Starts a blank form for a new server; it is saved once the connection succeeds. */
    fun startNewServer() {
        _addingNew.value = true
        _form.value = ServerForm()
        _state.value = UiState.NotConfigured
    }

    /** Switches to a saved server, repopulating the form and reconnecting to it. */
    fun selectServer(id: String) {
        if (id == activeServerId.value && !_addingNew.value) return
        viewModelScope.launch {
            _busy.value = true
            _addingNew.value = false
            settings.selectServer(id)
            val config = settings.config.first()
            loadFormFrom(config)
            switchActiveConnection(config)
            _busy.value = false
        }
    }

    /** Removes the active server and moves to whichever remains (or a disconnected state). */
    fun deleteActiveServer() {
        viewModelScope.launch {
            _busy.value = true
            _addingNew.value = false
            settings.deleteServer(activeServerId.value)
            val config = settings.config.first()
            loadFormFrom(config)
            switchActiveConnection(config)
            _busy.value = false
        }
    }

    /** Points the API provider at [config], drops the old server's cached data, and revalidates. */
    private suspend fun switchActiveConnection(config: ConnectionConfig) {
        Graph.apiProvider.updateConfig(config)
        Graph.apiProvider.clearCache()
        Graph.serverStatus.reportChecking()
        if (config.isConfigured) validate() else _state.value = UiState.NotConfigured
    }

    /**
     * Persists the connection: for login mode, exchanges username/password for a token first; for
     * API-key mode, stores the key directly. A new server is added as its own profile; otherwise the
     * active profile is updated. Then validates against the server.
     */
    fun saveAndConnect() {
        val form = _form.value
        if (!form.canSubmit) return
        viewModelScope.launch {
            _busy.value = true
            _state.value = UiState.Loading
            try {
                val token: String
                val tokenExpires: String
                when (form.authMode) {
                    AuthMode.API_KEY -> {
                        token = form.apiKey.trim()
                        tokenExpires = ""
                    }
                    AuthMode.LOGIN -> {
                        // Point the API provider at the host (no token yet) so login can be issued.
                        Graph.apiProvider.updateConfig(form.baseConfig())
                        val login = repository.login(form.username.trim(), form.password)
                        val issued = login.getOrNull()?.token
                        if (login.isFailure || issued.isNullOrBlank()) {
                            _state.value = UiState.Error(
                                login.exceptionOrNull()?.message ?: "Login failed. Check your username and password.",
                            )
                            return@launch
                        }
                        token = issued
                        tokenExpires = login.getOrNull()?.expiresAt.orEmpty()
                    }
                }

                if (_addingNew.value) {
                    settings.addServer(
                        ServerProfile(
                            id = UUID.randomUUID().toString(),
                            label = form.host.trim(),
                            scheme = form.scheme,
                            host = form.host.trim(),
                            port = form.port,
                            authMode = form.authMode,
                            token = token,
                            tokenExpiresAt = tokenExpires,
                            username = if (form.authMode == AuthMode.LOGIN) form.username.trim() else "",
                            activeGroupId = "",
                        ),
                    )
                    _addingNew.value = false
                } else {
                    val existing = settings.config.first()
                    settings.save(
                        existing.copy(
                            scheme = form.scheme,
                            host = form.host.trim(),
                            port = form.port,
                            authMode = form.authMode,
                            token = token,
                            tokenExpiresAt = tokenExpires,
                            username = if (form.authMode == AuthMode.LOGIN) form.username.trim() else "",
                            activeGroupId = "",
                        ),
                    )
                }

                val config = settings.config.first()
                Graph.apiProvider.updateConfig(config)
                // Clear the password from the form once it's been exchanged for a token.
                _form.value = _form.value.copy(password = "")
                validate()
            } finally {
                _busy.value = false
            }
        }
    }

    /** Clears the active server's stored session and returns to a disconnected state. */
    fun logout() {
        viewModelScope.launch {
            _busy.value = true
            repository.clearSession()
            val cleared = settings.config.first()
                .copy(token = "", tokenExpiresAt = "", username = "", activeGroupId = "")
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
