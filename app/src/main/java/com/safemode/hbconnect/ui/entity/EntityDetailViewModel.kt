package com.safemode.hbconnect.ui.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.LiveEvent
import com.safemode.hbconnect.data.remote.models.DuplicateOptions
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntityPatch
import com.safemode.hbconnect.data.remote.models.EntityPath
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshOnLiveEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EntityDetailViewModel(private val entityId: String) : ViewModel() {
    private val repository = Graph.repository

    private val _state = MutableStateFlow<UiState<EntityOut>>(UiState.Loading)
    val state: StateFlow<UiState<EntityOut>> = _state.asStateFlow()

    /** For locations: the entities contained directly within. */
    private val _children = MutableStateFlow<List<EntitySummary>>(emptyList())
    val children: StateFlow<List<EntitySummary>> = _children.asStateFlow()

    /** Ancestor path, root-first, ending with this entity. */
    private val _path = MutableStateFlow<List<EntityPath>>(emptyList())
    val path: StateFlow<List<EntityPath>> = _path.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    // Picker data for the move/retag quick actions, loaded lazily on first use.
    private val _locations = MutableStateFlow<List<EntitySummary>>(emptyList())
    val locations: StateFlow<List<EntitySummary>> = _locations.asStateFlow()

    private val _tags = MutableStateFlow<List<TagSummary>>(emptyList())
    val tags: StateFlow<List<TagSummary>> = _tags.asStateFlow()

    /** Whether the on-device QR action is enabled in Settings (off by default). */
    val showDeviceQr: StateFlow<Boolean> = Graph.settingsRepository.config
        .map { it.showDeviceQr }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var pickersLoaded = false

    init {
        load(showLoading = true)
        // Refresh if this entity (or anything it shows, like its children) changes server-side.
        refreshOnLiveEvent(LiveEvent.ENTITY) { if (!_deleted.value) load() }
    }

    /** Loads locations and tags for the quick-action pickers (once). */
    fun loadPickers() {
        if (pickersLoaded) return
        pickersLoaded = true
        viewModelScope.launch {
            _locations.value = repository.getLocations().getOrDefault(emptyList())
            _tags.value = repository.getTags().getOrDefault(emptyList())
        }
    }

    fun setQuantity(quantity: Double, onResult: (Boolean, String?) -> Unit) =
        patch(EntityPatch(id = entityId, quantity = quantity), onResult)

    fun move(parentId: String, onResult: (Boolean, String?) -> Unit) =
        patch(EntityPatch(id = entityId, parentId = parentId), onResult)

    fun setTags(tagIds: List<String>, onResult: (Boolean, String?) -> Unit) =
        patch(EntityPatch(id = entityId, tagIds = tagIds), onResult)

    /** Creates a tag and adds it to the picker list; [onResult] gets the new tag's id, or an error. */
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

    private fun patch(body: EntityPatch, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.patchEntity(entityId, body).fold(
                onSuccess = { load(showLoading = false); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    /** The Homebox web URL for this entity, encoded into the on-device QR code. */
    fun entityWebUrl(isLocation: Boolean): String = repository.entityWebUrl(entityId, isLocation)

    /** Fetches the server-rendered QR label PNG for this entity; [onResult] gets the bytes or an error. */
    fun fetchLabel(isLocation: Boolean, onResult: (ByteArray?, String?) -> Unit) {
        viewModelScope.launch {
            repository.downloadLabel(entityId, isLocation).fold(
                onSuccess = { onResult(it, null) },
                onFailure = { onResult(null, it.message) },
            )
        }
    }

    /** Duplicates this entity; [onResult] receives the new entity id on success. */
    fun duplicate(options: DuplicateOptions, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            repository.duplicateEntity(entityId, options).fold(
                onSuccess = { onResult(it.id, null) },
                onFailure = { onResult(null, it.message) },
            )
        }
    }

    fun load(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState.Loading
            val result = repository.getEntity(entityId)
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Failed to load.") },
            )
            val entity = result.getOrNull()
            if (entity != null) {
                _path.value = repository.getEntityPath(entityId).getOrDefault(emptyList())
            }
            if (entity?.isLocation == true) {
                _children.value = repository.getChildren(entityId).getOrDefault(emptyList())
            } else {
                _children.value = emptyList()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            load(showLoading = false)
            _refreshing.value = false
        }
    }

    fun delete(onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteEntity(entityId).fold(
                onSuccess = {
                    _deleted.value = true
                    onDone(true, null)
                },
                onFailure = { onDone(false, it.message) },
            )
        }
    }
}
