package com.safemode.hbconnect.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.Group
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the Collections screen: lists the groups the user belongs to, switches the active one, and
 * joins another collection from an invite code or link. Switching just saves
 * [com.safemode.hbconnect.data.settings.ConnectionConfig.activeGroupId]; Graph reacts by clearing
 * caches and refetching, and every request then carries the new X-Tenant header.
 */
class CollectionsViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<Group>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Group>>> = _state.asStateFlow()

    /** The id of the collection currently in effect (chosen, or the server default when none). */
    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
        refreshWhenServerReachable { viewModelScope.launch { reload() } }
    }

    fun load() {
        viewModelScope.launch { reload() }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload()
            _refreshing.value = false
        }
    }

    private suspend fun reload() {
        val config = settings.config.first()
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        // The default group (used when no override is set) is whatever GET /groups returns.
        val defaultId = repository.group().getOrNull()?.id
        _activeId.value = config.activeGroupId.ifBlank { defaultId }
        _state.value = repository.groupsAll().fold(
            onSuccess = { groups -> UiState.Success(groups.sortedBy { it.name?.lowercase() }) },
            onFailure = { UiState.Error(it.message ?: "Failed to load collections.") },
        )
    }

    /** Switches the active collection. No-op if already active. */
    fun select(group: Group) {
        val id = group.id ?: return
        if (id == _activeId.value) return
        viewModelScope.launch {
            _activeId.value = id
            val config = settings.config.first()
            settings.save(config.copy(activeGroupId = id))
        }
    }

    /**
     * Joins a collection from [input], which may be a raw invite code or a link containing a
     * `token=` parameter (or ending in the token). Reports the joined collection's name on success.
     */
    fun join(input: String, onResult: (joinedName: String?, error: String?) -> Unit) {
        val token = extractToken(input)
        if (token.isBlank()) {
            onResult(null, "Enter an invite code or link.")
            return
        }
        viewModelScope.launch {
            repository.acceptGroupInvitation(token).fold(
                onSuccess = { joined ->
                    reload()
                    onResult(joined.name ?: "collection", null)
                },
                onFailure = { onResult(null, it.message ?: "Couldn't join. Check the code.") },
            )
        }
    }

    private fun extractToken(input: String): String {
        val text = input.trim()
        Regex("[?&]token=([^&\\s]+)", RegexOption.IGNORE_CASE).find(text)?.let {
            return it.groupValues[1]
        }
        // A bare link with the token as its last path segment.
        if (text.contains("://")) return text.substringAfterLast('/').substringBefore('?').trim()
        return text
    }
}
