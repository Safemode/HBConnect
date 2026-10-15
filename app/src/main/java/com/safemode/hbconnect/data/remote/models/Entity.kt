package com.safemode.hbconnect.data.remote.models

/**
 * An entity type. In Homebox v0.26+ both locations and items are "entities"; the type's
 * [isLocation] flag is what distinguishes a location from an item.
 */
data class EntityTypeSummary(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val isLocation: Boolean? = null,
    val defaultTemplateId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Body for POST /api/v1/entity-types */
data class EntityTypeCreate(
    val name: String,
    val icon: String? = null,
    val isLocation: Boolean = false,
    val defaultTemplateId: String? = null,
)

/** Body for PUT /api/v1/entity-types/{id}. The path id is authoritative server-side. */
data class EntityTypeUpdate(
    val name: String,
    val icon: String? = null,
    val isLocation: Boolean = false,
    val defaultTemplateId: String? = null,
)

/** A row in the entity list (GET /api/v1/entities) and in nested children. */
data class EntitySummary(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val entityType: EntityTypeSummary? = null,
    val parent: EntitySummary? = null,
    val quantity: Double? = null,
    val itemCount: Double? = null,
    val insured: Boolean? = null,
    val archived: Boolean? = null,
    val assetId: String? = null,
    val imageId: String? = null,
    val thumbnailId: String? = null,
    val purchasePrice: Double? = null,
    val soldDate: String? = null,
    val tags: List<TagSummary>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val isLocation: Boolean get() = entityType?.isLocation == true
    val displayName: String get() = name?.ifBlank { null } ?: "Entity ${id?.take(8) ?: "?"}"
}

/** GET /api/v1/entities */
data class EntityListResult(
    val items: List<EntitySummary>? = null,
    val page: Int? = null,
    val pageSize: Int? = null,
    val total: Int? = null,
    val totalPrice: Double? = null,
)

/** Full entity (GET /api/v1/entities/{id}). */
data class EntityOut(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val entityType: EntityTypeSummary? = null,
    val parent: EntitySummary? = null,
    val children: List<EntitySummary>? = null,
    val quantity: Double? = null,
    val itemCount: Double? = null,
    val insured: Boolean? = null,
    val archived: Boolean? = null,
    val lifetimeWarranty: Boolean? = null,
    val assetId: String? = null,
    val serialNumber: String? = null,
    val modelNumber: String? = null,
    val manufacturer: String? = null,
    val notes: String? = null,
    val purchaseFrom: String? = null,
    val purchasePrice: Double? = null,
    val purchaseDate: String? = null,
    val soldTo: String? = null,
    val soldPrice: Double? = null,
    val soldDate: String? = null,
    val soldNotes: String? = null,
    val warrantyDetails: String? = null,
    val warrantyExpires: String? = null,
    val imageId: String? = null,
    val thumbnailId: String? = null,
    val totalPrice: Double? = null,
    val tags: List<TagSummary>? = null,
    val attachments: List<ItemAttachment>? = null,
    val fields: List<EntityFieldData>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val isLocation: Boolean get() = entityType?.isLocation == true
    val displayName: String get() = name?.ifBlank { null } ?: "Entity ${id?.take(8) ?: "?"}"
}

/** Body for POST /api/v1/entities */
data class EntityCreate(
    val name: String,
    val description: String? = null,
    val entityTypeId: String? = null,
    val parentId: String? = null,
    val quantity: Int? = null,
    val tagIds: List<String>? = null,
)

/** Body for PUT /api/v1/entities/{id} */
data class EntityUpdate(
    val id: String,
    val name: String,
    val description: String? = null,
    val entityTypeId: String? = null,
    val parentId: String? = null,
    val quantity: Int? = null,
    val insured: Boolean? = null,
    val archived: Boolean? = null,
    val lifetimeWarranty: Boolean? = null,
    val assetId: String? = null,
    val serialNumber: String? = null,
    val modelNumber: String? = null,
    val manufacturer: String? = null,
    val notes: String? = null,
    val purchaseFrom: String? = null,
    val purchasePrice: Double? = null,
    val purchaseDate: String? = null,
    val soldTo: String? = null,
    val soldPrice: Double? = null,
    val soldDate: String? = null,
    val soldNotes: String? = null,
    val warrantyDetails: String? = null,
    val warrantyExpires: String? = null,
    val syncChildEntityLocations: Boolean? = null,
    val fields: List<EntityFieldData>? = null,
    val tagIds: List<String>? = null,
)

/** Partial update (PATCH /api/v1/entities/{id}). Null fields are left unchanged server-side. */
data class EntityPatch(
    val id: String,
    val quantity: Double? = null,
    val parentId: String? = null,
    val entityTypeId: String? = null,
    val tagIds: List<String>? = null,
)

/** Options for POST /api/v1/entities/{id}/duplicate. */
data class DuplicateOptions(
    val copyMaintenance: Boolean = false,
    val copyAttachments: Boolean = true,
    val copyCustomFields: Boolean = true,
    val copyPrefix: String = "Copy of ",
)

/** Body for PUT /api/v1/entities/{id}/attachments/{attachmentId}; all fields are replaced. */
data class AttachmentUpdate(
    val type: String,
    val title: String,
    val primary: Boolean,
)

data class EntityFieldData(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val textValue: String? = null,
    val numberValue: Long? = null,
    val booleanValue: Boolean? = null,
)

data class ItemAttachment(
    val id: String? = null,
    val title: String? = null,
    val type: String? = null,
    val mimeType: String? = null,
    val path: String? = null,
    val primary: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Node in GET /api/v1/entities/tree. [type] is "location" or "item". */
data class TreeItem(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val children: List<TreeItem>? = null,
)

/** One hop in an entity's ancestor path (GET /api/v1/entities/{id}/path), root-first. */
data class EntityPath(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
)
