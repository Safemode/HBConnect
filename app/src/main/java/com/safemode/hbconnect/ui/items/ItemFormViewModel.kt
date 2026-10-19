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
import com.safemode.hbconnect.data.queue.OFFLINE_SAVED_MESSAGE
import com.safemode.hbconnect.data.queue.OpKind
import com.safemode.hbconnect.data.queue.isUndelivered
import com.safemode.hbconnect.data.EntityEditPayload
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
 * Parses a typed price, accepting "." or "," as the decimal separator (many locales' keyboards only
 * offer a comma). When both appear, the last one is the decimal separator and the other groups
 * thousands, so "1,234.56" and "1.234,56" both read as 1234.56. A single comma followed by exactly
 * three digits ("1,234") is read as a thousands separator; otherwise it is the decimal ("12,50").
 * Returns null for blank or non-numeric input.
 */
internal fun parsePrice(raw: String): Double? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    val lastDot = s.lastIndexOf('.')
    val lastComma = s.lastIndexOf(',')
    val normalized = when {
        lastDot >= 0 && lastComma >= 0 ->
            if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        lastComma >= 0 ->
            if (s.count { it == ',' } == 1 && s.length - lastComma - 1 != 3) s.replace(',', '.')
            else s.replace(",", "")
        else -> s
    }
    return normalized.toDoubleOrNull()
}

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

    /** True when the loaded/created entity already has a primary image. */
    private fun hasImage(): Boolean =
        original?.imageId?.isNotBlank() == true || original?.thumbnailId?.isNotBlank() == true

    /**
     * Uploads the pending photos to entity [id]; the first one that uploads becomes primary when the
     * item has no image yet. A photo that fails to upload is handed to the upload queue (which keeps
     * its own copy and retries it) rather than deleted, so it is never lost. Returns a warning for the
     * caller to show, or null when every photo went up.
     */
    private suspend fun uploadPendingPhotos(id: String, itemName: String): String? {
        val paths = _photos.value
        if (paths.isEmpty()) return null
        var primaryPending = !hasImage()
        var unreadable = 0
        var firstError: String? = null
        val failed = mutableListOf<String>()
        paths.forEach { path ->
            val file = File(path)
            if (!file.isFile) {
                unreadable++
                return@forEach
            }
            val result = repository.uploadAttachment(
                entityId = id,
                file = file,
                fileName = file.name,
                mimeType = "image/jpeg",
                type = "photo",
                primary = primaryPending,
            )
            if (result.isSuccess) {
                primaryPending = false
                runCatching { file.delete() }
            } else {
                if (firstError == null) firstError = result.exceptionOrNull()?.message
                failed += path
            }
        }
        if (failed.isNotEmpty()) {
            Graph.uploadQueue.enqueueUploadPhotos(id, failed, "Photos for $itemName", primaryFirst = primaryPending)
            failed.forEach { runCatching { File(it).delete() } }
        }
        _photos.value = emptyList()
        if (usesDraft) ItemDraftStore.photoPaths = emptyList()
        return when {
            failed.isNotEmpty() -> {
                val one = failed.size == 1
                val subject = if (one) "a photo" else "${failed.size} photos"
                val reason = firstError?.let { " ($it)" }.orEmpty()
                "Saved, but $subject didn't upload$reason. ${if (one) "It" else "They"} will retry from the upload queue."
            }
            unreadable > 0 -> "Saved, but a photo couldn't be read."
            else -> null
        }
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
        if (f.parentId.isNullOrBlank()) {
            onResult(false, "Location is required.")
            return
        }
        if (f.purchasePrice.isNotBlank() && parsePrice(f.purchasePrice) == null) {
            onResult(false, "Price isn't a valid number.")
            return
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                // itemId when editing, or createdId when completing an item made earlier this session.
                val existingId = itemId?.ifBlank { null } ?: createdId

                // The version the form started from, so an edit only changes the fields the user
                // touched and keeps anything changed on the server meanwhile (see mergeEdit).
                val base = existingId?.let { id -> original?.let { buildUpdate(formFrom(it), id) } }

                // Offline: save to the upload queue instead of hitting the server.
                if (!Graph.uploadQueue.online()) {
                    saveOffline(f, existingId, base)
                    onResult(true, OFFLINE_SAVED_MESSAGE)
                    return@launch
                }
                var savedId: String? = null
                var error: String? = null
                if (existingId != null) {
                    val edited = buildUpdate(f, existingId)
                    val result = if (base != null) {
                        repository.mergeUpdateEntity(existingId, base, edited)
                    } else {
                        repository.updateEntity(existingId, edited)
                    }
                    if (result.isSuccess) {
                        savedId = existingId
                    } else if (isUndelivered(result.exceptionOrNull())) {
                        // The connection dropped before the server got the request: queue it.
                        saveOffline(f, existingId, base)
                        onResult(true, OFFLINE_SAVED_MESSAGE)
                        return@launch
                    } else {
                        error = result.exceptionOrNull()?.message
                    }
                } else {
                    // The create endpoint accepts only a subset of fields; persist the rest with a
                    // follow-up update so details entered on a new item (serial, notes, purchase,
                    // warranty, insured) aren't lost.
                    val created = repository.createEntity(buildCreate(f))
                    val entity = created.getOrNull()
                    if (entity == null && isUndelivered(created.exceptionOrNull())) {
                        // Nothing reached the server, so queueing can't make a duplicate.
                        saveOffline(f, null, null)
                        onResult(true, OFFLINE_SAVED_MESSAGE)
                        return@launch
                    }
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
                        val photoWarning = uploadPendingPhotos(savedId, f.name.trim())
                        // Entity saved; a photo failure is reported but not treated as a hard failure.
                        onResult(true, photoWarning)
                    }
                }
            } finally {
                _saving.value = false
            }
        }
    }

    /**
     * Saves to the upload queue. A new item (with its photos) goes in as one create op that carries
     * the full field set; an edit goes in as a merge op (or a plain update when there's no loaded
     * [base]) plus a photo-upload op for any new photos.
     */
    private suspend fun saveOffline(f: ItemForm, existingId: String?, base: EntityUpdate?) {
        if (existingId != null) {
            val edited = buildUpdate(f, existingId)
            Graph.uploadQueue.enqueue(
                kind = if (base != null) OpKind.MERGE_ENTITY else OpKind.UPDATE_ENTITY,
                payload = if (base != null) EntityEditPayload(base, edited) else edited,
                targetId = existingId,
                label = "Edit item: ${f.name.trim()}",
            )
            if (_photos.value.isNotEmpty()) {
                Graph.uploadQueue.enqueueUploadPhotos(
                    existingId,
                    _photos.value,
                    "Photos for ${f.name.trim()}",
                    primaryFirst = !hasImage(),
                )
            }
        } else {
            Graph.uploadQueue.enqueueCreateEntity(buildUpdate(f, ""), _photos.value, "New item: ${f.name.trim()}")
        }
        clearDraft()
        _photos.value.forEach { runCatching { File(it).delete() } }
        _photos.value = emptyList()
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
            purchasePrice = parsePrice(f.purchasePrice),
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
