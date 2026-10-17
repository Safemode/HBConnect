package com.safemode.hbconnect.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.LiveEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Invokes [onEvent] whenever the server announces a change in one of [events] over the live-updates
 * WebSocket — e.g. an item added or edited elsewhere — so an open screen refreshes itself. A short
 * debounce collapses the burst of signals a bulk change produces into a single refresh. Mirrors
 * [refreshWhenServerReachable], and is a no-op while live updates are off or disconnected (no events
 * arrive).
 */
@OptIn(FlowPreview::class)
fun ViewModel.refreshOnLiveEvent(vararg events: LiveEvent, onEvent: () -> Unit) {
    val wanted = events.toSet()
    viewModelScope.launch {
        Graph.liveUpdates.events
            .filter { it in wanted }
            .debounce(400)
            .collect { onEvent() }
    }
}
