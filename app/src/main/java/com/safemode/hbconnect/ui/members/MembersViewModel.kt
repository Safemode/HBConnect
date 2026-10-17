package com.safemode.hbconnect.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.GroupInvitation
import com.safemode.hbconnect.data.remote.models.GroupInvitationCreated
import com.safemode.hbconnect.data.remote.models.GroupMember
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the Members screen for the active collection: lists members and pending invitations, and
 * (owner-only, enforced server-side) removes members, creates invitations and revokes them. An
 * invitation's token is returned only once, on creation, so the screen surfaces it immediately.
 */
class MembersViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<GroupMember>>>(UiState.Loading)
    val state: StateFlow<UiState<List<GroupMember>>> = _state.asStateFlow()

    private val _invitations = MutableStateFlow<List<GroupInvitation>>(emptyList())
    val invitations: StateFlow<List<GroupInvitation>> = _invitations.asStateFlow()

    /** The signed-in user's id, to mark "You" and hide self-removal. */
    private val _selfId = MutableStateFlow<String?>(null)
    val selfId: StateFlow<String?> = _selfId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        load()
        refreshWhenServerReachable { load() }
    }

    fun load() {
        viewModelScope.launch {
            val config = settings.config.first()
            if (!config.isConfigured) {
                _state.value = UiState.NotConfigured
                return@launch
            }
            _selfId.value = repository.self().getOrNull()?.id
            _invitations.value = repository.groupInvitations().getOrDefault(emptyList())
            _state.value = repository.groupMembers().fold(
                onSuccess = { members -> UiState.Success(members.sortedBy { it.name?.lowercase() }) },
                onFailure = { UiState.Error(it.message ?: "Failed to load members.") },
            )
        }
    }

    /** Creates an invitation good for [uses] joins; returns the created invite (with its token). */
    fun createInvitation(uses: Int, onResult: (GroupInvitationCreated?, String?) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.createGroupInvitation(uses)
            _busy.value = false
            result.fold(
                onSuccess = { created ->
                    reloadInvitations()
                    onResult(created, null)
                },
                onFailure = { onResult(null, it.message) },
            )
        }
    }

    fun deleteInvitation(id: String, onResult: (Boolean, String?) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.deleteGroupInvitation(id)
            _busy.value = false
            if (result.isSuccess) reloadInvitations()
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun removeMember(id: String, onResult: (Boolean, String?) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.removeGroupMember(id)
            _busy.value = false
            if (result.isSuccess) load()
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    private suspend fun reloadInvitations() {
        _invitations.value = repository.groupInvitations().getOrDefault(emptyList())
    }
}
