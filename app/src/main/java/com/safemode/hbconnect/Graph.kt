package com.safemode.hbconnect

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.safemode.hbconnect.data.HomeboxRepository
import com.safemode.hbconnect.data.remote.ApiProvider
import com.safemode.hbconnect.data.remote.NetworkMonitor
import com.safemode.hbconnect.data.remote.ServerReachability
import com.safemode.hbconnect.data.remote.ServerStatus
import com.safemode.hbconnect.data.settings.AuthMode
import com.safemode.hbconnect.data.settings.SettingsRepository
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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

    private lateinit var networkMonitor: NetworkMonitor

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        val app = context.applicationContext
        settingsRepository = SettingsRepository(app)
        serverStatus = ServerStatus()
        networkMonitor = NetworkMonitor(app, serverStatus)
        apiProvider = ApiProvider(app.cacheDir, serverStatus, networkMonitor::isOnline)
        repository = HomeboxRepository(apiProvider)

        // Keep the API provider's config in sync with saved settings.
        settingsRepository.config
            .onEach { apiProvider.updateConfig(it) }
            .launchIn(appScope)

        // Refresh a login token that's getting close to expiry, so the session survives. Best-effort
        // and gated to login-mode tokens; API keys and failures are left untouched.
        appScope.launch {
            val cfg = settingsRepository.config.first()
            apiProvider.updateConfig(cfg)
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
    private fun tokenNeedsRefresh(expiresAt: String): Boolean {
        if (expiresAt.isBlank()) return true
        val expiry = runCatching { OffsetDateTime.parse(expiresAt) }.getOrNull() ?: return true
        return OffsetDateTime.now().isAfter(expiry.minus(REFRESH_WINDOW))
    }

    private val REFRESH_WINDOW: Duration = Duration.ofDays(7)
    private const val PROBE_MIN_BACKOFF_MS = 5_000L
    private const val PROBE_MAX_BACKOFF_MS = 60_000L
}
