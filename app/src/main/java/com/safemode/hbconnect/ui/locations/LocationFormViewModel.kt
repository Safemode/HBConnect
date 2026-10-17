package com.safemode.hbconnect.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityUpdate
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
                _form.value = LocationForm(
                    name = entity.name.orEmpty(),
                    description = entity.description.orEmpty(),
                    parentId = entity.parent?.id,
                    entityTypeId = entity.entityType?.id ?: locationTypeId,
                )
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
            val result = if (isEdit) {
                // The update endpoint replaces the whole entity, so carry through the fields this
                // form doesn't expose (quantity, custom fields) from the loaded original, otherwise
                // saving a name/parent change would wipe them.
                val prior = original
                repository.updateEntity(
                    locationId!!,
                    EntityUpdate(
                        id = locationId,
                        name = f.name.trim(),
                        description = f.description.trim().ifBlank { null },
                        entityTypeId = f.entityTypeId,
                        parentId = f.parentId,
                        quantity = prior?.quantity?.toInt(),
                        syncChildEntityLocations = false,
                        fields = prior?.fields,
                    ),
                ).map { }
            } else {
                repository.createEntity(
                    EntityCreate(
                        name = f.name.trim(),
                        description = f.description.trim().ifBlank { null },
                        entityTypeId = f.entityTypeId,
                        parentId = f.parentId,
                    ),
                ).map { }
            }
            _saving.value = false
            result.fold(
                onSuccess = { onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }
}
