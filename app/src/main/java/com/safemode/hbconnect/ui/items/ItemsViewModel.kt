package com.safemode.hbconnect.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityPatch
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.data.remote.LiveEvent
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshOnLiveEvent
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ItemsViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<EntitySummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<EntitySummary>>> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _baseUrl = MutableStateFlow<String?>(null)
    val baseUrl: StateFlow<String?> = _baseUrl.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    // ---- Multi-select / bulk edit ----
    /** Ids of items currently selected; non-empty means selection mode is active. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    // Picker data for the bulk-edit dialogs, loaded lazily on first use.
    private val _locations = MutableStateFlow<List<EntitySummary>>(emptyList())
    val locations: StateFlow<List<EntitySummary>> = _locations.asStateFlow()

    private val _tags = MutableStateFlow<List<TagSummary>>(emptyList())
    val tags: StateFlow<List<TagSummary>> = _tags.asStateFlow()

    private var pickersLoaded = false

    init {
        // Re-query as the search text settles or the archived toggle flips.
        viewModelScope.launch {
            combine(
                _query.debounce(300).distinctUntilChanged(),
                _showArchived,
            ) { _, _ -> }.collect { startReload(showLoading = true) }
        }
        refreshWhenServerReachable { reloadSilently() }
        refreshOnLiveEvent(LiveEvent.ENTITY) { reloadSilently() }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setShowArchived(value: Boolean) {
        _showArchived.value = value
    }

    fun load() {
        startReload(showLoading = true)
    }

    fun reloadSilently() {
        startReload(showLoading = false)
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            startReload(showLoading = false).join()
            _refreshing.value = false
        }
    }

    // The reload in flight. Live events, reconnects, typing and pull-to-refresh can each trigger one,
    // so a new reload cancels the previous one: an older, slower response can't land after a newer
    // one and show stale results.
    private var loadJob: Job? = null

    private fun startReload(showLoading: Boolean): Job {
        loadJob?.cancel()
        return viewModelScope.launch { reload(showLoading) }.also { loadJob = it }
    }

    /** Loads locations and tags for the bulk-edit dialogs (once per session). */
    fun loadPickers() {
        if (pickersLoaded) return
        pickersLoaded = true
        viewModelScope.launch {
            _locations.value = repository.getLocations().getOrDefault(emptyList())
            _tags.value = repository.getTags().getOrDefault(emptyList())
        }
    }

    /** Toggles an item's selection; the first toggle enters selection mode. */
    fun toggleSelection(id: String) = _selection.update {
        if (id in it) it - id else it + id
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun selectAll() {
        _selection.value = (_state.value as? UiState.Success)?.data
            ?.mapNotNull { it.id }?.toSet() ?: emptySet()
    }

    /** Moves every selected item under [parentId]. */
    fun bulkMove(parentId: String, onResult: (Boolean, String?) -> Unit) =
        applyToSelection(onResult) { id -> repository.patchEntity(id, EntityPatch(id = id, parentId = parentId)).map {} }

    /** Sets the quantity on every selected item. */
    fun bulkSetQuantity(quantity: Double, onResult: (Boolean, String?) -> Unit) =
        applyToSelection(onResult) { id -> repository.patchEntity(id, EntityPatch(id = id, quantity = quantity)).map {} }

    /** Permanently deletes every selected item. */
    fun bulkDelete(onResult: (Boolean, String?) -> Unit) =
        applyToSelection(onResult) { id -> repository.deleteEntity(id) }

    /** Adds [tagIds] to every selected item, merging with each item's existing tags. */
    fun bulkAddTags(tagIds: List<String>, onResult: (Boolean, String?) -> Unit) {
        val byId = (_state.value as? UiState.Success)?.data.orEmpty().associateBy { it.id }
        applyToSelection(onResult) { id ->
            val existing = byId[id]?.tags?.mapNotNull { it.id }?.toSet() ?: emptySet()
            repository.patchEntity(id, EntityPatch(id = id, tagIds = (existing + tagIds).toList())).map {}
        }
    }

    /** Creates a tag, adds it to the picker list, and returns its id (or an error). */
    fun createTag(name: String, onResult: (String?, String?) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onResult(null, "Tag name is required.")
            return
        }
        viewModelScope.launch {
            repository.createTag(TagCreate(name = trimmed)).fold(
                onSuccess = { created ->
                    val id = created.id
                    if (id != null) {
                        _tags.value = (_tags.value + TagSummary(id = id, name = created.name ?: trimmed))
                            .sortedBy { it.name?.lowercase() }
                    }
                    onResult(id, null)
                },
                onFailure = { onResult(null, it.message) },
            )
        }
    }

    /** Runs [op] over each selected id, then clears the selection and reloads. */
    private fun applyToSelection(
        onResult: (Boolean, String?) -> Unit,
        op: suspend (String) -> Result<Unit>,
    ) {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) {
            onResult(true, null)
            return
        }
        viewModelScope.launch {
            _applying.value = true
            var firstError: String? = null
            for (id in ids) {
                val result = op(id)
                if (result.isFailure && firstError == null) {
                    firstError = result.exceptionOrNull()?.message
                }
            }
            _applying.value = false
            clearSelection()
            startReload(showLoading = false)
            onResult(firstError == null, firstError)
        }
    }

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        _baseUrl.value = config.baseUrl()
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.getItems(
            query = _query.value,
            sort = config.itemSortOrder,
            includeArchived = _showArchived.value,
        ).fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to load items.") },
        )
    }
}
