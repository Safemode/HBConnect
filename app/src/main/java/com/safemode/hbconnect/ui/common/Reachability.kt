package com.safemode.hbconnect.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.ServerReachability
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Invokes [onReachable] whenever the server transitions to reachable — e.g. the background probe
 * reconnected after launch or after an outage — so a screen currently showing cached data pulls
 * fresh data. The state at subscription time is skipped (the ViewModel's own initial load covers
 * that), so this only fires on an actual reconnect.
 */
fun ViewModel.refreshWhenServerReachable(onReachable: () -> Unit) {
    viewModelScope.launch {
        Graph.serverStatus.state
            .map { it == ServerReachability.REACHABLE }
            .distinctUntilChanged()
            .drop(1)
            .collect { reachable -> if (reachable) onReachable() }
    }
}
