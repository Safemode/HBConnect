package com.safemode.hbconnect.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.data.remote.models.EntityUpdate
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Editable item form. Strings are kept raw and parsed on save. */
data class ItemForm(
    val name: String = "",
    val description: String = "",
    val quantity: String = "1",
    val parentId: String? = null,
    val entityTypeId: String? = null,
    val tagIds: Set<String> = emptySet(),
    val manufacturer: String = "",
    val modelNumber: String = "",
    val serialNumber: String = "",
    val notes: String = "",
    val purchaseFrom: String = "",
    val purchasePrice: String = "",
    val purchaseDate: String = "",
    val warrantyExpires: String = "",
    val lifetimeWarranty: Boolean = false,
    val warrantyDetails: String = "",
    val insured: Boolean = false,
)

class ItemFormViewModel(
    private val itemId: String?,
    private val presetParentId: String?,
) : ViewModel() {
    private val repository = Graph.repository

    val isEdit: Boolean = !itemId.isNullOrBlank()

    private val _form = MutableStateFlow(ItemForm(parentId = presetParentId?.ifBlank { null }))
    val form: StateFlow<ItemForm> = _form.asStateFlow()

    private val _locations = MutableStateFlow<List<EntitySummary>>(emptyList())
    val locations: StateFlow<List<EntitySummary>> = _locations.asStateFlow()

    private val _tags = MutableStateFlow<List<TagSummary>>(emptyList())
    val tags: StateFlow<List<TagSummary>> = _tags.asStateFlow()

    private val _itemTypes = MutableStateFlow<List<EntityTypeSummary>>(emptyList())

    private val _load = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val load: StateFlow<UiState<Unit>> = _load.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /**
     * The loaded entity when editing. Homebox's update does a full field replace, so fields this
     * form doesn't expose (sold info, custom fields, asset id, …) are carried through from here to
     * avoid wiping them.
     */
    private var original: com.safemode.hbconnect.data.remote.models.EntityOut? = null

    init {
        viewModelScope.launch {
            _locations.value = repository.getLocations().getOrDefault(emptyList())
            _tags.value = repository.getTags().getOrDefault(emptyList())
            _itemTypes.value = repository.getItemTypes().getOrDefault(emptyList())

            if (isEdit) {
                val result = repository.getEntity(itemId!!)
                val entity = result.getOrNull()
                if (result.isFailure || entity == null) {
                    _load.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load item.")
                    return@launch
                }
                original = entity
                _form.value = ItemForm(
                    name = entity.name.orEmpty(),
                    description = entity.description.orEmpty(),
                    quantity = (entity.quantity?.toInt() ?: 1).toString(),
                    parentId = entity.parent?.id,
                    entityTypeId = entity.entityType?.id,
                    tagIds = entity.tags?.mapNotNull { it.id }?.toSet() ?: emptySet(),
                    manufacturer = entity.manufacturer.orEmpty(),
                    modelNumber = entity.modelNumber.orEmpty(),
                    serialNumber = entity.serialNumber.orEmpty(),
                    notes = entity.notes.orEmpty(),
                    purchaseFrom = entity.purchaseFrom.orEmpty(),
                    purchasePrice = entity.purchasePrice?.let { formatNum(it) }.orEmpty(),
                    purchaseDate = entity.purchaseDate?.substringBefore('T').orEmpty(),
                    warrantyExpires = entity.warrantyExpires?.substringBefore('T').orEmpty(),
                    lifetimeWarranty = entity.lifetimeWarranty == true,
                    warrantyDetails = entity.warrantyDetails.orEmpty(),
                    insured = entity.insured == true,
                )
            } else {
                // Default the entity type to the first item type when creating.
                _form.value = _form.value.copy(entityTypeId = _itemTypes.value.firstOrNull()?.id)
            }
            _load.value = UiState.Success(Unit)
        }
    }

    fun update(transform: (ItemForm) -> ItemForm) {
        _form.value = transform(_form.value)
    }

    fun toggleTag(tagId: String) {
        val current = _form.value.tagIds
        _form.value = _form.value.copy(
            tagIds = if (tagId in current) current - tagId else current + tagId,
        )
    }

    /** Validates and saves. Calls [onResult] with success and an optional error message. */
    fun save(onResult: (Boolean, String?) -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) {
            onResult(false, "Name is required.")
            return
        }
        viewModelScope.launch {
            _saving.value = true
            val tagList = f.tagIds.toList()
            val result = if (isEdit) {
                val prior = original
                repository.updateEntity(
                    itemId!!,
                    EntityUpdate(
                        id = itemId,
                        name = f.name.trim(),
                        description = f.description.trim().ifBlank { null },
                        entityTypeId = f.entityTypeId,
                        parentId = f.parentId,
                        quantity = f.quantity.toIntOrNull(),
                        insured = f.insured,
                        archived = prior?.archived,
                        lifetimeWarranty = f.lifetimeWarranty,
                        assetId = prior?.assetId,
                        manufacturer = f.manufacturer.trim().ifBlank { null },
                        modelNumber = f.modelNumber.trim().ifBlank { null },
                        serialNumber = f.serialNumber.trim().ifBlank { null },
                        notes = f.notes.trim().ifBlank { null },
                        purchaseFrom = f.purchaseFrom.trim().ifBlank { null },
                        purchasePrice = f.purchasePrice.toDoubleOrNull(),
                        purchaseDate = f.purchaseDate.ifBlank { null },
                        // Preserved from the loaded entity (not editable in this form).
                        soldTo = prior?.soldTo,
                        soldPrice = prior?.soldPrice,
                        soldDate = prior?.soldDate,
                        soldNotes = prior?.soldNotes,
                        warrantyExpires = if (f.lifetimeWarranty) null else f.warrantyExpires.ifBlank { null },
                        warrantyDetails = f.warrantyDetails.trim().ifBlank { null },
                        syncChildEntityLocations = prior?.let { false },
                        fields = prior?.fields,
                        tagIds = tagList,
                    ),
                ).map { }
            } else {
                repository.createEntity(
                    EntityCreate(
                        name = f.name.trim(),
                        description = f.description.trim().ifBlank { null },
                        entityTypeId = f.entityTypeId,
                        parentId = f.parentId,
                        quantity = f.quantity.toIntOrNull() ?: 1,
                        tagIds = tagList.ifEmpty { null },
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

    private fun formatNum(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
