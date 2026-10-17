package com.safemode.hbconnect

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.safemode.hbconnect.data.HomeboxRepository
import com.safemode.hbconnect.data.remote.ApiProvider
import com.safemode.hbconnect.data.remote.LiveUpdatesClient
import com.safemode.hbconnect.data.remote.NetworkMonitor
import com.safemode.hbconnect.data.remote.ServerReachability
import com.safemode.hbconnect.data.remote.ServerStatus
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.notifications.MaintenanceReminderScheduler
import com.safemode.hbconnect.data.settings.AuthMode
import com.safemode.hbconnect.data.settings.SettingsRepository
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manual dependency container. Initialized once from [HBConnectApp]. */
object Graph {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var apiProvider: ApiProvider
        private set

    lateinit var repository: HomeboxRepository
        private set

    lateinit var serverStatus: ServerStatus
        private set

    lateinit var liveUpdates: LiveUpdatesClient
        private set

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var appContext: Context

    /** The group's configured currency (symbol/decimals), resolved for money formatting; null until loaded. */
    private val _currency = MutableStateFlow<Currency?>(null)
    val currency: StateFlow<Currency?> = _currency.asStateFlow()

    /**
     * An entity id a notification asked the app to open, consumed by the UI once navigated. Set from
     * [MainActivity] when launched from a maintenance reminder.
     */
    private val _pendingEntityId = MutableStateFlow<String?>(null)
    val pendingEntityId: StateFlow<String?> = _pendingEntityId.asStateFlow()

    fun requestOpenEntity(id: String?) {
        if (!id.isNullOrBlank()) _pendingEntityId.value = id
    }

    fun consumePendingEntity() {
        _pendingEntityId.value = null
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        settingsRepository = SettingsRepository(app)
        serverStatus = ServerStatus()
        networkMonitor = NetworkMonitor(app, serverStatus)
        apiProvider = ApiProvider(app.cacheDir, serverStatus, networkMonitor::isOnline)
        repository = HomeboxRepository(apiProvider)
        liveUpdates = LiveUpdatesClient(appScope)

        // Keep the API provider's config in sync with saved settings.
        settingsRepository.config
            .onEach { apiProvider.updateConfig(it) }
            .launchIn(appScope)

        // Register or cancel the periodic maintenance-reminder check as the setting changes. The
        // worker itself runs only while the setting stays on, is configured, and may post; it does
        // its own network fetch on WorkManager's schedule, independent of the app being open.
        settingsRepository.config
            .map { it.maintenanceReminders }
            .distinctUntilChanged()
            .onEach { MaintenanceReminderScheduler.update(appContext, it) }
            .launchIn(appScope)

        // Drive the live-updates WebSocket from the foreground state and settings: connect only
        // while the app is foregrounded, live updates are enabled, and the server is configured;
        // reconnect when the host/credentials change; disconnect on background. Reconnects are
        // skipped for unrelated settings changes (theme, sort, …) that don't touch the connection.
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    settingsRepository.config
                        .map { cfg -> cfg.takeIf { it.liveUpdates && it.isConfigured } }
                        .distinctUntilChanged { old, new ->
                            old?.baseUrl() == new?.baseUrl() && old?.authHeader() == new?.authHeader()
                        }
                        .collect { liveUpdates.connectTo(it) }
                } finally {
                    liveUpdates.connectTo(null)
                }
            }
        }

        // Refresh a login token that's getting close to expiry, so the session survives. Best-effort
        // and gated to login-mode tokens; API keys and failures are left untouched.
        appScope.launch {
            val cfg = settingsRepository.config.first()
            apiProvider.updateConfig(cfg)
            if (cfg.isConfigured) loadCurrency()
            if (cfg.authMode == AuthMode.LOGIN && cfg.token.isNotBlank() && tokenNeedsRefresh(cfg.tokenExpiresAt)) {
                repository.refresh().onSuccess { tr ->
                    val newToken = tr.token
                    if (!newToken.isNullOrBlank()) {
                        val updated = cfg.copy(
                            token = newToken,
                            tokenExpiresAt = tr.expiresAt?.ifBlank { null } ?: cfg.tokenExpiresAt,
                        )
                        settingsRepository.save(updated)
                        apiProvider.updateConfig(updated)
                    }
                }
            }
        }

        // Reachability check: whenever a network is present but the server isn't confirmed
        // reachable (at launch, or after it went down), quietly probe it off the UI thread. Gated
        // to the foreground via ProcessLifecycleOwner so the app never fires requests every few
        // seconds in the background.
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runReachabilityLoop()
            }
        }
    }

    private suspend fun runReachabilityLoop() = withContext(Dispatchers.IO) {
        var backoffMs = PROBE_MIN_BACKOFF_MS
        while (isActive) {
            if (networkMonitor.isOnline() && !serverStatus.isReachable()) {
                serverStatus.report(apiProvider.probeReachable())
            }
            if (serverStatus.isReachable()) {
                backoffMs = PROBE_MIN_BACKOFF_MS
                serverStatus.state.first { it != ServerReachability.REACHABLE }
            } else {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(PROBE_MAX_BACKOFF_MS)
            }
        }
    }

    /**
     * True when the token expires within [REFRESH_WINDOW] (or the expiry can't be parsed, so we
     * refresh to be safe). An already-expired token also returns true — the refresh will simply
     * fail and the user re-logs in.
     */
    /**
     * Resolves the group's currency code against the server's currency list and caches it for
     * money formatting. Best-effort: failures leave the previous value (prices then show unadorned).
     */
    suspend fun loadCurrency() {
        val code = repository.group().getOrNull()?.currency?.ifBlank { null } ?: return
        val match = repository.currencies().getOrNull()
            ?.firstOrNull { it.code.equals(code, ignoreCase = true) }
        _currency.value = match ?: Currency(code = code)
    }

    private fun tokenNeedsRefresh(expiresAt: String): Boolean {
        if (expiresAt.isBlank()) return true
        val expiry = runCatching { OffsetDateTime.parse(expiresAt) }.getOrNull() ?: return true
        return OffsetDateTime.now().isAfter(expiry.minus(REFRESH_WINDOW))
    }

    private val REFRESH_WINDOW: Duration = Duration.ofDays(7)
    private const val PROBE_MIN_BACKOFF_MS = 5_000L
    private const val PROBE_MAX_BACKOFF_MS = 60_000L
}
