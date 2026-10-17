package com.safemode.hbconnect.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.data.remote.models.TagUpdate
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.data.remote.LiveEvent
import com.safemode.hbconnect.ui.common.refreshOnLiveEvent
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the Tags screen: lists the group's tags and creates/updates/deletes them (tags can nest via
 * [TagCreate.parentId]). Follows the shared list-screen pattern - [load] with a full-screen spinner,
 * [reloadSilently] without one - and refreshes silently both when the server becomes reachable and
 * when a live-update TAG event arrives, so a change made elsewhere shows up without a manual reload.
 */
class TagsViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<TagSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<TagSummary>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
        refreshWhenServerReachable { reloadSilently() }
        refreshOnLiveEvent(LiveEvent.TAG) { reloadSilently() }
    }

    fun load() {
        viewModelScope.launch { reload(showLoading = true) }
    }

    fun reloadSilently() {
        viewModelScope.launch { reload(showLoading = false) }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(showLoading = false)
            _refreshing.value = false
        }
    }

    fun create(
        name: String,
        description: String,
        parentId: String?,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repository.createTag(
                TagCreate(
                    name = name.trim(),
                    description = description.trim().ifBlank { null },
                    parentId = parentId,
                ),
            ).fold(
                onSuccess = { reloadSilently(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    fun update(
        id: String,
        name: String,
        description: String,
        parentId: String?,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repository.updateTag(
                id,
                TagUpdate(
                    name = name.trim(),
                    description = description.trim().ifBlank { null },
                    parentId = parentId,
                ),
            ).fold(
                onSuccess = { reloadSilently(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    fun delete(id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteTag(id).fold(
                onSuccess = { reloadSilently(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.getTags().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to load tags.") },
        )
    }
}
