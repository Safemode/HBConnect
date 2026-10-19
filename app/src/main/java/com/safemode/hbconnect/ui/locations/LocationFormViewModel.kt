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
import com.safemode.hbconnect.data.queue.OpKind
import com.safemode.hbconnect.data.queue.submitWrite
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocationForm(
    val name: String = "",
    val description: String = "",
    val parentId: String? = null,
    val entityTypeId: String? = null,
)

/**
 * Backs the add/edit location form. [locationId] null means "create"; non-null means "edit" (see
 * [isEdit]). On create, [presetParentId] seeds the parent so "Add location here" lands under the
 * right place. The parent picker excludes the location being edited so it can't be made its own
 * parent. [load] gates the form behind a loading/error state while the existing entity and the
 * location types are fetched; [saving] drives the save button's progress state.
 */
class LocationFormViewModel(
    private val locationId: String?,
    presetParentId: String?,
) : ViewModel() {
    private val repository = Graph.repository

    val isEdit: Boolean = !locationId.isNullOrBlank()

    private val _form = MutableStateFlow(LocationForm(parentId = presetParentId?.ifBlank { null }))
    val form: StateFlow<LocationForm> = _form.asStateFlow()

    /** Candidate parent locations (excludes the location being edited). */
    private val _parents = MutableStateFlow<List<EntitySummary>>(emptyList())
    val parents: StateFlow<List<EntitySummary>> = _parents.asStateFlow()

    private val _load = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val load: StateFlow<UiState<Unit>> = _load.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private var original: EntityOut? = null

    /** The form as first loaded when editing: the base the user's changes are measured against. */
    private var loadedForm: LocationForm? = null

    init {
        viewModelScope.launch {
            val allLocations = repository.getLocations().getOrDefault(emptyList())
            _parents.value = allLocations.filter { it.id != locationId }
            val locationTypeId = repository.getLocationTypes().getOrNull()?.firstOrNull()?.id

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

    fun save(onResult: (Boolean, String?) -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) {
            onResult(false, "Name is required.")
            return
        }
        viewModelScope.launch {
            _saving.value = true
            val done: (Boolean, String?) -> Unit = { ok, message -> _saving.value = false; onResult(ok, message) }
            if (isEdit) {
                // The update endpoint replaces the whole entity, so every field this form doesn't
                // expose (tags, notes, archived, asset id, purchase/warranty/sold info, custom fields)
                // is carried from the loaded original. The save is then merged onto the server's
                // current version (see mergeEdit), so only the fields edited here change and edits
                // made elsewhere meanwhile - including while this change waits offline - survive.
                val id = locationId!!
                val carried = original?.toUpdate() ?: EntityUpdate(id = id, name = "")
                fun withForm(form: LocationForm) = carried.copy(
                    id = id,
                    name = form.name.trim(),
                    description = form.description.trim().ifBlank { null },
                    entityTypeId = form.entityTypeId,
                    parentId = form.parentId,
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
                        onResult = done,
                    )
                } else {
                    Graph.uploadQueue.submitWrite(
                        kind = OpKind.UPDATE_ENTITY,
                        payload = update,
                        targetId = id,
                        label = "Edit location: ${update.name}",
                        direct = { repository.updateEntity(id, update).map {} },
                        onResult = done,
                    )
                }
            } else {
                val create = EntityCreate(
                    name = f.name.trim(),
                    description = f.description.trim().ifBlank { null },
                    entityTypeId = f.entityTypeId,
                    parentId = f.parentId,
                )
                // CREATE_ENTITY carries a full EntityUpdate (blank id) so the offline replay can
                // recreate the location exactly; the online path just uses the create body directly.
                val full = EntityUpdate(
                    id = "",
                    name = create.name,
                    description = create.description,
                    entityTypeId = create.entityTypeId,
                    parentId = create.parentId,
                )
                Graph.uploadQueue.submitWrite(
                    kind = OpKind.CREATE_ENTITY,
                    payload = full,
                    label = "New location: ${create.name}",
                    direct = { repository.createEntity(create).map {} },
                    onResult = done,
                )
            }
        }
    }
}
