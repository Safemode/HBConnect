package com.safemode.hbconnect.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.queue.QueueTarget
import com.safemode.hbconnect.data.queue.QueuedOp
import com.safemode.hbconnect.data.remote.ServerReachability
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Upload queue screen. Exposes the pending outbox and whether the server is reachable, and
 * forwards the two user actions (retry now, remove one) to [com.safemode.hbconnect.data.queue.UploadQueueManager].
 * The list drains itself automatically on reconnect; "retry" just nudges a drain sooner.
 */
class QueueViewModel : ViewModel() {
    private val queue = Graph.uploadQueue

    val ops: StateFlow<List<QueuedOp>> = queue.queue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Ids of ops saved for a different server, collection or account than the active one. They stay
     * queued but only upload once their own server, collection and account are active again.
     */
    val elsewhere: StateFlow<Set<String>> = combine(queue.queue, Graph.settingsRepository.config) { ops, cfg ->
        val target = QueueTarget(server = cfg.serverId, tenant = cfg.activeGroupId, account = cfg.accountId)
        ops.filterNot { target.accepts(it) }.map { it.id }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val online: StateFlow<Boolean> = Graph.serverStatus.state
        .map { it == ServerReachability.REACHABLE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun retryAll() {
        viewModelScope.launch { queue.drain() }
    }

    fun remove(opId: String) {
        viewModelScope.launch { queue.remove(opId) }
    }
}
