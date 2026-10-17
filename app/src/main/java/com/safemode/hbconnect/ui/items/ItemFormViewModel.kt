package com.safemode.hbconnect.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.BarcodeProduct
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.data.remote.models.EntityUpdate
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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
    val archived: Boolean = false,
)

/**
 * In-memory draft of the generic "New item" form (the bottom-bar Add). It outlives the form's
 * back-stack entry, so leaving the form - switching tabs, pressing back - keeps what was typed, and
 * reopening Add restores it. A successful save clears it. Edits and "add item here" (preset parent)
 * flows don't use it. Process death discards the draft.
 */
object ItemDraftStore {
    var draft: ItemForm? = null

    /** Local file paths of photos taken for the draft but not yet uploaded. */
    var photoPaths: List<String> = emptyList()
}

/**
 * Backs the add/edit item form. [itemId] null means "create", non-null means "edit" (see [isEdit]);
 * [presetParentId] seeds the parent for the "Add item here" flow.
 *
 * Three Homebox quirks shape the save path:
 *  - The create endpoint accepts only a subset of fields, so a new item with details the create
 *    body can't carry (serial, notes, purchase, warranty, insured) is created and then updated
 *    ([needsDetailUpdate]); the created id is remembered so a retry completes it rather than making
 *    a duplicate.
 *  - Update is a full-field replace, so fields this form doesn't expose (sold info, custom fields,
 *    asset id) are carried through from the loaded [original] so a save doesn't wipe them.
 *  - Photos taken on the form are only local files until the item exists; they're uploaded as
 *    attachments after the save succeeds.
 *
 * The plain bottom-bar Add also drafts into [ItemDraftStore] so leaving and returning keeps typed
 * input; editing and "add here" always start clean.
 */
class ItemFormViewModel(
    private val itemId: String?,
    private val presetParentId: String?,
) : ViewModel() {
    private val repository = Graph.repository

    val isEdit: Boolean = !itemId.isNullOrBlank()

    /** Only the plain Add flow drafts; editing and add-here (preset parent) always start clean. */
    private val usesDraft: Boolean = !isEdit && presetParentId.isNullOrBlank()

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

    /** Local paths of photos taken on the form; uploaded as attachments once the item is saved. */
    private val _photos = MutableStateFlow<List<String>>(emptyList())
    val photos: StateFlow<List<String>> = _photos.asStateFlow()

    /**
     * The loaded entity when editing. Homebox's update does a full field replace, so fields this
     * form doesn't expose (sold info, custom fields, asset id, …) are carried through from here to
     * avoid wiping them.
     */
    private var original: com.safemode.hbconnect.data.remote.models.EntityOut? = null

    /** Id of an item created this session; lets a re-save complete it instead of creating a duplicate. */
    private var createdId: String? = null

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
                _form.value = formFrom(entity)
            } else {
                val restored = if (usesDraft) ItemDraftStore.draft else null
                // Restore a saved draft, otherwise default the entity type to the first item type.
                _form.value = restored
                    ?: _form.value.copy(entityTypeId = _itemTypes.value.firstOrNull()?.id)
                if (usesDraft) _photos.value = ItemDraftStore.photoPaths
            }
            _load.value = UiState.Success(Unit)
        }
    }

    fun update(transform: (ItemForm) -> ItemForm) {
        setForm(transform(_form.value))
    }

    /** Builds the editable form from a loaded entity. */
    private fun formFrom(entity: EntityOut) = ItemForm(
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
        archived = entity.archived == true,
    )

    /**
     * Clears the form. Editing reverts to the loaded values; a new item goes back to blank defaults
     * and drops the draft. Also clears the created-this-session id so the next save starts fresh -
     * which is what "Create and add another" relies on.
     */
    fun reset() {
        if (isEdit) {
            original?.let { _form.value = formFrom(it) }
        } else {
            createdId = null
            original = null
            _form.value = ItemForm(
                parentId = presetParentId?.ifBlank { null },
                entityTypeId = _itemTypes.value.firstOrNull()?.id,
            )
            _photos.value.forEach { runCatching { File(it).delete() } }
            _photos.value = emptyList()
            clearDraft()
        }
    }

    /** Applies a new form value and mirrors it into the draft store for the plain Add flow. */
    private fun setForm(new: ItemForm) {
        _form.value = new
        if (usesDraft) ItemDraftStore.draft = new
    }

    /** Discards the saved draft (form text and pending photos) once the item has been committed. */
    private fun clearDraft() {
        if (usesDraft) {
            ItemDraftStore.draft = null
            ItemDraftStore.photoPaths = emptyList()
        }
    }

    /** Records a photo taken on the form, to upload after the item is saved. */
    fun addPhoto(path: String) {
        _photos.value = _photos.value + path
        if (usesDraft) ItemDraftStore.photoPaths = _photos.value
    }

    /** Drops a pending photo and deletes its local file. */
    fun removePhoto(path: String) {
        runCatching { File(path).delete() }
        _photos.value = _photos.value - path
        if (usesDraft) ItemDraftStore.photoPaths = _photos.value
    }

    /** Uploads the pending photos to entity [id]; the first becomes primary when it has no image. */
    private suspend fun uploadPendingPhotos(id: String): String? {
        val paths = _photos.value
        if (paths.isEmpty()) return null
        val hasImage = original?.imageId?.isNotBlank() == true || original?.thumbnailId?.isNotBlank() == true
        var firstError: String? = null
        paths.forEachIndexed { index, path ->
            val file = File(path)
            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes == null) {
                if (firstError == null) firstError = "a photo couldn't be read"
                return@forEachIndexed
            }
            repository.uploadAttachment(
                entityId = id,
                fileName = file.name,
                mimeType = "image/jpeg",
                bytes = bytes,
                type = "photo",
                primary = !hasImage && index == 0,
            ).onFailure { if (firstError == null) firstError = it.message }
            runCatching { file.delete() }
        }
        _photos.value = emptyList()
        if (usesDraft) ItemDraftStore.photoPaths = emptyList()
        return firstError
    }

    /** Looks up a scanned barcode; [onResult] gets the candidate products, or an error message. */
    fun lookupBarcode(code: String, onResult: (List<BarcodeProduct>?, String?) -> Unit) {
        viewModelScope.launch {
            repository.searchFromBarcode(code).fold(
                onSuccess = { onResult(it, null) },
                onFailure = { onResult(null, it.message) },
            )
        }
    }

    /**
     * Fills blank form fields from a looked-up product (keeps anything already typed). Values are
     * clamped to Homebox's create limits (name/manufacturer/model 255, description 1000) because
     * barcode databases often return very long titles/descriptions that would 422 on save.
     */
    fun applyBarcodeProduct(p: BarcodeProduct) {
        setForm(
            _form.value.copy(
                name = _form.value.name.ifBlank { p.item?.name?.trim()?.take(255).orEmpty() },
                description = _form.value.description.ifBlank { p.item?.description?.trim()?.take(1000).orEmpty() },
                manufacturer = _form.value.manufacturer.ifBlank { p.manufacturer?.trim()?.take(255).orEmpty() },
                modelNumber = _form.value.modelNumber.ifBlank { p.modelNumber?.trim()?.take(255).orEmpty() },
            ),
        )
    }

    fun toggleTag(tagId: String) {
        val current = _form.value.tagIds
        setForm(
            _form.value.copy(
                tagIds = if (tagId in current) current - tagId else current + tagId,
            ),
        )
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
                        setForm(_form.value.copy(tagIds = _form.value.tagIds + id))
                    }
                    onResult(true, null)
                },
                onFailure = { onResult(false, it.message) },
            )
        }
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
            try {
                // itemId when editing, or createdId when completing an item made earlier this session.
                val existingId = itemId?.ifBlank { null } ?: createdId
                var savedId: String? = null
                var error: String? = null
                if (existingId != null) {
                    val result = repository.updateEntity(existingId, buildUpdate(f, existingId))
                    if (result.isSuccess) savedId = existingId else error = result.exceptionOrNull()?.message
                } else {
                    // The create endpoint accepts only a subset of fields; persist the rest with a
                    // follow-up update so details entered on a new item (serial, notes, purchase,
                    // warranty, insured) aren't lost.
                    val created = repository.createEntity(buildCreate(f))
                    val entity = created.getOrNull()
                    if (entity == null) {
                        error = created.exceptionOrNull()?.message
                    } else {
                        original = entity
                        createdId = entity.id
                        val id = entity.id
                        if (id == null || !needsDetailUpdate(f)) {
                            savedId = id
                        } else {
                            val detail = repository.updateEntity(id, buildUpdate(f, id))
                            if (detail.isSuccess) {
                                savedId = id
                            } else {
                                // The item exists; keep photos pending and let the user retry.
                                error = "Item created, but some details didn't save: " +
                                    "${detail.exceptionOrNull()?.message}. Tap Save again to retry."
                            }
                        }
                    }
                }

                when {
                    savedId == null -> onResult(false, error)
                    error != null -> onResult(false, error) // created but detail update failed
                    else -> {
                        clearDraft()
                        val photoError = uploadPendingPhotos(savedId)
                        // Entity saved; a photo failure is reported but not treated as a hard failure.
                        onResult(true, photoError?.let { "Saved, but a photo didn't upload: $it" })
                    }
                }
            } finally {
                _saving.value = false
            }
        }
    }

    private fun buildCreate(f: ItemForm) = EntityCreate(
        name = f.name.trim(),
        description = f.description.trim().ifBlank { null },
        entityTypeId = f.entityTypeId,
        parentId = f.parentId,
        quantity = f.quantity.toIntOrNull() ?: 1,
        tagIds = f.tagIds.toList().ifEmpty { null },
        manufacturer = f.manufacturer.trim().ifBlank { null },
        modelNumber = f.modelNumber.trim().ifBlank { null },
    )

    /** Full-replace update body, carrying through fields this form doesn't expose from [original]. */
    private fun buildUpdate(f: ItemForm, id: String): EntityUpdate {
        val prior = original
        return EntityUpdate(
            id = id,
            name = f.name.trim(),
            description = f.description.trim().ifBlank { null },
            entityTypeId = f.entityTypeId,
            parentId = f.parentId,
            quantity = f.quantity.toIntOrNull(),
            insured = f.insured,
            archived = f.archived,
            lifetimeWarranty = f.lifetimeWarranty,
            assetId = prior?.assetId,
            manufacturer = f.manufacturer.trim().ifBlank { null },
            modelNumber = f.modelNumber.trim().ifBlank { null },
            serialNumber = f.serialNumber.trim().ifBlank { null },
            notes = f.notes.trim().ifBlank { null },
            purchaseFrom = f.purchaseFrom.trim().ifBlank { null },
            purchasePrice = f.purchasePrice.toDoubleOrNull(),
            purchaseDate = f.purchaseDate.ifBlank { null },
            // Preserved from the loaded/created entity (not editable in this form).
            soldTo = prior?.soldTo,
            soldPrice = prior?.soldPrice,
            soldDate = prior?.soldDate,
            soldNotes = prior?.soldNotes,
            warrantyExpires = if (f.lifetimeWarranty) null else f.warrantyExpires.ifBlank { null },
            warrantyDetails = f.warrantyDetails.trim().ifBlank { null },
            syncChildEntityLocations = prior?.let { false },
            fields = prior?.fields,
            tagIds = f.tagIds.toList(),
        )
    }

    /** True when a new item has fields the create endpoint can't set, needing a follow-up update. */
    private fun needsDetailUpdate(f: ItemForm): Boolean =
        f.serialNumber.isNotBlank() || f.notes.isNotBlank() ||
            f.purchaseFrom.isNotBlank() || f.purchasePrice.isNotBlank() ||
            f.purchaseDate.isNotBlank() || f.warrantyExpires.isNotBlank() ||
            f.lifetimeWarranty || f.warrantyDetails.isNotBlank() || f.insured

    private fun formatNum(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
