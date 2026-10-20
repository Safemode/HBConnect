package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntityUpdate

/**
 * An edit to an existing entity, kept as the version the form started from ([base]) and the version
 * the user saved ([edited]). Homebox's update is a full replace, so sending [edited] as-is would
 * overwrite anything changed on the server since [base] was loaded; [mergeEdit] applies only the
 * user's changes onto the server's current version instead.
 */
data class EntityEditPayload(
    val base: EntityUpdate,
    val edited: EntityUpdate,
)

/** Trims a server timestamp to the yyyy-MM-dd the forms send. */
private fun String?.dateOnly(): String? = this?.substringBefore('T')?.ifBlank { null }

/** The full-replace update body that leaves this entity as it is (dates trimmed to yyyy-MM-dd). */
fun EntityOut.toUpdate(): EntityUpdate = EntityUpdate(
    id = id.orEmpty(),
    name = name.orEmpty(),
    description = description,
    entityTypeId = entityType?.id,
    parentId = parent?.id,
    // Without this, a full replace would drop a sub-item's own location.
    locationId = locationId,
    quantity = quantity?.toInt(),
    insured = insured,
    archived = archived,
    lifetimeWarranty = lifetimeWarranty,
    assetId = assetId,
    serialNumber = serialNumber,
    modelNumber = modelNumber,
    manufacturer = manufacturer,
    notes = notes,
    purchaseFrom = purchaseFrom,
    purchasePrice = purchasePrice,
    purchaseDate = purchaseDate.dateOnly(),
    soldTo = soldTo,
    soldPrice = soldPrice,
    soldDate = soldDate,
    soldNotes = soldNotes,
    warrantyDetails = warrantyDetails,
    warrantyExpires = warrantyExpires.dateOnly(),
    syncChildEntityLocations = false,
    fields = fields,
    tagIds = tags?.mapNotNull { it.id },
)

/**
 * Three-way merge of an edit onto the entity's [current] server state: each field the user changed
 * (it differs between [base] and [edited]) takes the edited value, and every other field keeps the
 * server's current value. Tags merge per tag: the ones the user added or removed are applied to the
 * server's current set, so tags added elsewhere in the meantime are kept.
 */
fun mergeEdit(base: EntityUpdate, edited: EntityUpdate, current: EntityUpdate): EntityUpdate {
    fun <T> pick(b: T, e: T, c: T): T = if (e != b) e else c

    val baseTags = base.tagIds.orEmpty().toSet()
    val editedTags = edited.tagIds.orEmpty().toSet()
    val tags = if (baseTags == editedTags) {
        current.tagIds
    } else {
        val removed = baseTags - editedTags
        val added = editedTags - baseTags
        (current.tagIds.orEmpty().filterNot { it in removed } + added.filterNot { it in current.tagIds.orEmpty() })
    }

    return EntityUpdate(
        id = current.id.ifBlank { edited.id },
        name = pick(base.name, edited.name, current.name),
        description = pick(base.description, edited.description, current.description),
        entityTypeId = pick(base.entityTypeId, edited.entityTypeId, current.entityTypeId),
        parentId = pick(base.parentId, edited.parentId, current.parentId),
        locationId = pick(base.locationId, edited.locationId, current.locationId),
        quantity = pick(base.quantity, edited.quantity, current.quantity),
        insured = pick(base.insured, edited.insured, current.insured),
        archived = pick(base.archived, edited.archived, current.archived),
        lifetimeWarranty = pick(base.lifetimeWarranty, edited.lifetimeWarranty, current.lifetimeWarranty),
        assetId = pick(base.assetId, edited.assetId, current.assetId),
        serialNumber = pick(base.serialNumber, edited.serialNumber, current.serialNumber),
        modelNumber = pick(base.modelNumber, edited.modelNumber, current.modelNumber),
        manufacturer = pick(base.manufacturer, edited.manufacturer, current.manufacturer),
        notes = pick(base.notes, edited.notes, current.notes),
        purchaseFrom = pick(base.purchaseFrom, edited.purchaseFrom, current.purchaseFrom),
        purchasePrice = pick(base.purchasePrice, edited.purchasePrice, current.purchasePrice),
        purchaseDate = pick(base.purchaseDate, edited.purchaseDate, current.purchaseDate),
        soldTo = pick(base.soldTo, edited.soldTo, current.soldTo),
        soldPrice = pick(base.soldPrice, edited.soldPrice, current.soldPrice),
        soldDate = pick(base.soldDate, edited.soldDate, current.soldDate),
        soldNotes = pick(base.soldNotes, edited.soldNotes, current.soldNotes),
        warrantyDetails = pick(base.warrantyDetails, edited.warrantyDetails, current.warrantyDetails),
        warrantyExpires = pick(base.warrantyExpires, edited.warrantyExpires, current.warrantyExpires),
        syncChildEntityLocations = edited.syncChildEntityLocations,
        fields = pick(base.fields, edited.fields, current.fields),
        tagIds = tags,
    )
}
