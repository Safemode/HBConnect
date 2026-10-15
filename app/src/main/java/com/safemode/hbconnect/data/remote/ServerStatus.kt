package com.safemode.hbconnect.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the app stands with the server right now. */
enum class ServerReachability {
    /** Haven't confirmed yet — showing cached data while a probe runs. */
    CHECKING,

    /** Server responded; live data is being used. */
    REACHABLE,

    /** Confirmed down/unroutable — serving cached data only. */
    UNREACHABLE,
}

/**
 * Tracks server reachability, updated by network calls and the background probe (see [ApiProvider]
 * and the reachability loop in Graph). Drives the app-wide offline banner. Starts in [CHECKING] so
 * the app serves cached data immediately at launch instead of blocking on a connection attempt.
 */
class ServerStatus {
    private val _state = MutableStateFlow(ServerReachability.CHECKING)
    val state: StateFlow<ServerReachability> = _state.asStateFlow()

    fun isReachable(): Boolean = _state.value == ServerReachability.REACHABLE

    /** Reports a definitive result from a real network call. */
    fun report(reachable: Boolean) {
        _state.value = if (reachable) ServerReachability.REACHABLE else ServerReachability.UNREACHABLE
    }

    /** Marks the status as unknown again (e.g. the network just changed), prompting a re-probe. */
    fun reportChecking() {
        _state.value = ServerReachability.CHECKING
    }
}
