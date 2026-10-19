package com.safemode.hbconnect.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.EntityEditPayload
import com.safemode.hbconnect.data.toUpdate
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityUpdate
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.data.queue.OFFLINE_SAVED_MESSAGE
import com.safemode.hbconnect.data.queue.OpKind
import com.safemode.hbconnect.data.queue.isUndelivered
import com.safemode.hbconnect.data.queue.submitWrite
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.uploadFormPhotos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class LocationForm(
    val name: String = "",
    val description: String = "",
    val parentId: String? = null,
    val entityTypeId: String? = null,
    val tagIds: Set<String> = emptySet(),
)

/**
 * Backs the add/edit location form. [locationId] null means "create"; non-null means "edit" (see
 * [isEdit]). On create, [presetParentId] seeds the parent so "Add location here" lands under the
 * right place. The parent picker excludes the location being edited so it can't be made its own
 * parent. [load] gates the form behind a loading/error state while the existing entity and the
 * location types are fetched; [saving] drives the save button's progress state.
 *
 * Photos taken or chosen while creating are only local files until the location exists; they are
 * uploaded as attachments after the create succeeds. When editing, the screen manages attachments
 * directly (see AttachmentsViewModel), so [photos] is only used on create.
 */
class LocationFormViewModel(
    private val locationId: String?,
    private val presetParentId: String?,
) : ViewModel() {
    private val repository = Graph.repository

    val isEdit: Boolean = !locationId.isNullOrBlank()

    private val _form = MutableStateFlow(LocationForm(parentId = presetParentId?.ifBlank { null }))
    val form: StateFlow<LocationForm> = _form.asStateFlow()

    /** Candidate parent locations (excludes the location being edited). */
    private val _parents = MutableStateFlow<List<EntitySummary>>(emptyList())
    val parents: StateFlow<List<EntitySummary>> = _parents.asStateFlow()

    private val _tags = MutableStateFlow<List<TagSummary>>(emptyList())
    val tags: StateFlow<List<TagSummary>> = _tags.asStateFlow()

    private val _load = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val load: StateFlow<UiState<Unit>> = _load.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Local paths of photos for a new location; uploaded as attachments once it is created. */
    private val _photos = MutableStateFlow<List<String>>(emptyList())
    val photos: StateFlow<List<String>> = _photos.asStateFlow()

    private var original: EntityOut? = null

    /** The form as first loaded when editing: the base the user's changes are measured against. */
    private var loadedForm: LocationForm? = null

    private var locationTypeId: String? = null

    init {
        viewModelScope.launch {
            val allLocations = repository.getLocations().getOrDefault(emptyList())
            _parents.value = allLocations.filter { it.id != locationId }
            _tags.value = repository.getTags().getOrDefault(emptyList())
            locationTypeId = repository.getLocationTypes().getOrNull()?.firstOrNull()?.id

            if (isEdit) {
                val result = repository.getEntity(locationId!!)
                val entity = result.getOrNull()
                if (result.isFailure || entity == null) {
                    _load.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load location.")
                    return@launch
                }
                original = entity
                val loaded = LocationForm(
                    name = entity.name.orEmpty(),
                    description = entity.description.orEmpty(),
                    parentId = entity.parent?.id,
                    entityTypeId = entity.entityType?.id ?: locationTypeId,
                    tagIds = entity.tags?.mapNotNull { it.id }?.toSet() ?: emptySet(),
                )
                loadedForm = loaded
                _form.value = loaded
            } else {
                _form.value = _form.value.copy(entityTypeId = locationTypeId)
            }
            _load.value = UiState.Success(Unit)
        }
    }

    fun update(transform: (LocationForm) -> LocationForm) {
        _form.value = transform(_form.value)
    }

    /** Clears a new-location form for the next entry ("Create and add another"). */
    fun reset() {
        _form.value = LocationForm(parentId = presetParentId?.ifBlank { null }, entityTypeId = locationTypeId)
        _photos.value.forEach { runCatching { File(it).delete() } }
        _photos.value = emptyList()
    }

    fun toggleTag(tagId: String) {
        val current = _form.value.tagIds
        _form.value = _form.value.copy(tagIds = if (tagId in current) current - tagId else current + tagId)
    }

    /** Creates a tag, adds it to the pickable list, and selects it. [onResult] reports success. */
    fun createTag(name: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Tag name is required.")
            return
        }
        viewModelScope.launch {
            repository.createTag(TagCreate(name = trimmed)).fold(
                onSuccess = { created ->
                    val id = created.id
                    if (id != null) {
                        _tags.value = (_tags.value + TagSummary(id = id, name = created.name ?: trimmed))
                            .sortedBy { it.name?.lowercase() }
                        _form.value = _form.value.copy(tagIds = _form.value.tagIds + id)
                    }
                    onResult(true, null)
                },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    /** Records a photo for the new location, to upload after it is created. */
    fun addPhoto(path: String) {
        _photos.value = _photos.value + path
    }

    /** Drops a pending photo and deletes its local file. */
    fun removePhoto(path: String) {
        runCatching { File(path).delete() }
        _photos.value = _photos.value - path
    }

    /**
     * Validates and saves. [onResult] gets success and an optional message: on failure the error, on
     * success the offline notice or a photo-upload warning.
     */
    fun save(onResult: (Boolean, String?) -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) {
            onResult(false, "Name is required.")
            return
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                if (isEdit) saveEdit(f, onResult) else saveNew(f, onResult)
            } finally {
                _saving.value = false
            }
        }
    }

    private suspend fun saveEdit(f: LocationForm, onResult: (Boolean, String?) -> Unit) {
        // The update endpoint replaces the whole entity, so every field this form doesn't expose
        // (notes, archived, asset id, purchase/warranty/sold info, custom fields) is carried from the
        // loaded original. The save is then merged onto the server's current version (see mergeEdit),
        // so only the fields edited here change and edits made elsewhere meanwhile - including while
        // this change waits offline - survive.
        val id = locationId!!
        val carried = original?.toUpdate() ?: EntityUpdate(id = id, name = "")
        fun withForm(form: LocationForm) = carried.copy(
            id = id,
            name = form.name.trim(),
            description = form.description.trim().ifBlank { null },
            entityTypeId = form.entityTypeId,
            parentId = form.parentId,
            tagIds = form.tagIds.toList(),
            syncChildEntityLocations = false,
        )
        val update = withForm(f)
        val base = loadedForm?.let(::withForm)
        if (base != null) {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.MERGE_ENTITY,
                payload = EntityEditPayload(base, update),
                targetId = id,
                label = "Edit location: ${update.name}",
                direct = { repository.mergeUpdateEntity(id, base, update).map {} },
                onResult = onResult,
            )
        } else {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.UPDATE_ENTITY,
                payload = update,
                targetId = id,
                label = "Edit location: ${update.name}",
                direct = { repository.updateEntity(id, update).map {} },
                onResult = onResult,
            )
        }
    }

    /**
     * Creates the location, then uploads its photos (the first becomes primary). Offline, or if the
     * connection drops before the server gets the create, the location and its photos go into the
     * upload queue as one create op that carries the full field set.
     */
    private suspend fun saveNew(f: LocationForm, onResult: (Boolean, String?) -> Unit) {
        val create = EntityCreate(
            name = f.name.trim(),
            description = f.description.trim().ifBlank { null },
            entityTypeId = f.entityTypeId,
            parentId = f.parentId,
            tagIds = f.tagIds.toList().ifEmpty { null },
        )
        if (Graph.uploadQueue.online()) {
            val created = repository.createEntity(create)
            val entity = created.getOrNull()
            if (entity != null) {
                val warning = entity.id?.let { uploadFormPhotos(it, _photos.value, create.name, primaryFirst = true) }
                _photos.value = emptyList()
                onResult(true, warning)
                return
            }
            if (!isUndelivered(created.exceptionOrNull())) {
                onResult(false, created.exceptionOrNull()?.message)
                return
            }
        }
        // Nothing reached the server, so queueing can't make a duplicate.
        val full = EntityUpdate(
            id = "",
            name = create.name,
            description = create.description,
            entityTypeId = create.entityTypeId,
            parentId = create.parentId,
            tagIds = f.tagIds.toList(),
        )
        Graph.uploadQueue.enqueueCreateEntity(full, _photos.value, "New location: ${create.name}")
        _photos.value.forEach { runCatching { File(it).delete() } }
        _photos.value = emptyList()
        onResult(true, OFFLINE_SAVED_MESSAGE)
    }
}
