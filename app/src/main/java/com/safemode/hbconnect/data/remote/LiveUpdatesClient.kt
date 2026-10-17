package com.safemode.hbconnect.data.remote

import com.safemode.hbconnect.data.settings.ConnectionConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** A category of server-side change announced over the live-updates WebSocket. */
enum class LiveEvent { ENTITY, TAG, USER, EXPORT, IMPORT }

/**
 * Keeps a single WebSocket open to Homebox's `/api/v1/ws/events` endpoint and republishes the
 * server's mutation signals as [LiveEvent]s on [events]. The server's messages are payload-free
 * (`{"event":"entity.mutation"}` and friends) plus a periodic `{"event":"ping"}`, so a [LiveEvent]
 * means only "something in this category changed in your group" - collectors react by refreshing.
 *
 * Connection is driven entirely by [connectTo]: pass a configured [ConnectionConfig] to connect (or
 * reconnect against new host/credentials) and `null` to disconnect. The bearer token is sent as the
 * `Authorization` header on the handshake, so it never appears in the URL. Dropped connections are
 * retried with exponential backoff while a target is set; OkHttp's own ping interval detects a dead
 * peer and triggers a reconnect.
 */
class LiveUpdatesClient(scope: CoroutineScope) {

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    /** Whether the WebSocket is currently open. Purely informational. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Long-lived read; liveness is handled by the ping interval below, not a read timeout.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** The config to stay connected to, or null to stay disconnected. */
    private val target = MutableStateFlow<ConnectionConfig?>(null)

    init {
        scope.launch { runLoop() }
    }

    /** Sets the connection target: a configured config to (re)connect, or null to disconnect. */
    fun connectTo(config: ConnectionConfig?) {
        target.value = config?.takeIf { it.isConfigured }
    }

    private suspend fun runLoop() {
        target.collectLatest { cfg ->
            if (cfg == null) {
                _connected.value = false
                return@collectLatest
            }
            var backoffMs = MIN_BACKOFF_MS
            while (currentCoroutineContext().isActive) {
                val cleanClose = runSession(cfg)
                _connected.value = false
                backoffMs = if (cleanClose) MIN_BACKOFF_MS
                else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                delay(backoffMs)
            }
        }
    }

    /**
     * Opens one WebSocket and suspends until it closes or fails. Returns true on a clean close,
     * false on failure (so the caller can back off). Cancellation (a new [target]) cancels the
     * socket via the `finally` block.
     */
    private suspend fun runSession(config: ConnectionConfig): Boolean {
        val requestBuilder = Request.Builder()
            .url(config.baseUrl() + WS_PATH)
            // The server advertises this subprotocol for its auth'd event stream.
            .addHeader("Sec-WebSocket-Protocol", SUBPROTOCOL)
        config.authHeader()?.let { requestBuilder.header("Authorization", it) }
        // Scope the event stream to the active collection, matching the REST calls.
        config.tenantHeader()?.let { requestBuilder.header("X-Tenant", it) }

        val closed = CompletableDeferred<Boolean>()
        val socket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseEvent(text)?.let { _events.tryEmit(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                closed.complete(false)
            }
        })
        return try {
            closed.await()
        } catch (e: CancellationException) {
            socket.cancel()
            throw e
        } finally {
            socket.cancel()
        }
    }

    /** Maps a server message like `{"event":"entity.mutation"}` to a [LiveEvent]; null for ping/unknown. */
    private fun parseEvent(text: String): LiveEvent? {
        val name = EVENT_REGEX.find(text)?.groupValues?.get(1) ?: return null
        return when (name) {
            "entity.mutation" -> LiveEvent.ENTITY
            "tag.mutation" -> LiveEvent.TAG
            "user.mutation" -> LiveEvent.USER
            "export.mutation" -> LiveEvent.EXPORT
            "import.mutation" -> LiveEvent.IMPORT
            else -> null // "ping" and anything unrecognized are ignored.
        }
    }

    private companion object {
        const val WS_PATH = "api/v1/ws/events"
        const val SUBPROTOCOL = "hb-auth"
        const val NORMAL_CLOSURE = 1000
        const val MIN_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        val EVENT_REGEX = """"event"\s*:\s*"([^"]+)"""".toRegex()
    }
}
