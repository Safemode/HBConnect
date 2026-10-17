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
        val defaultGroup = repository.group().getOrNull()
        val activeId = config.activeGroupId.ifBlank { defaultGroup?.id }
        _activeId.value = activeId
        val result = repository.groupsAll()
        _state.value = result.fold(
            onSuccess = { groups -> UiState.Success(groups.sortedBy { it.name?.lowercase() }) },
            onFailure = { UiState.Error(it.message ?: "Failed to load collections.") },
        )
        // Keep the glanceable active-collection name (shown on the More hub) in sync, so a rename
        // done here - or anywhere - is reflected there too.
        val active = result.getOrNull()?.firstOrNull { it.id == activeId } ?: defaultGroup
        Graph.setActiveGroupName(active?.name)
    }

    /**
     * Renames [group] to [newName]. Sends the collection's current currency unchanged (the server
     * writes both fields), fetching it first only if the list didn't carry one. Targets the
     * collection's own tenant, so a collection other than the active one can be renamed too.
     */
    fun rename(group: Group, newName: String, onResult: (Boolean, String?) -> Unit) {
        val id = group.id
        val name = newName.trim()
        if (id == null || name.isBlank()) {
            onResult(false, "Enter a name.")
            return
        }
        viewModelScope.launch {
            val currency = group.currency?.ifBlank { null }
                ?: repository.group(tenant = id).getOrNull()?.currency
                ?: ""
            repository.updateGroup(name, currency, tenant = id).fold(
                onSuccess = {
                    reload()
                    onResult(true, null)
                },
                onFailure = { onResult(false, it.message ?: "Couldn't rename the collection.") },
            )
        }
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
