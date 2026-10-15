package com.safemode.hbconnect.data.remote.models

/** A row in GET /api/v1/templates. */
data class EntityTemplateSummary(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * GET /api/v1/templates/{id}. Only the scalar default fields this app edits are modelled; the
 * server also stores custom fields, a default location/tags, and warranty/purchase flags, which
 * are left untouched by create/update calls that omit them.
 */
data class EntityTemplateOut(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val defaultName: String? = null,
    val defaultDescription: String? = null,
    val defaultManufacturer: String? = null,
    val defaultModelNumber: String? = null,
    val defaultQuantity: Double? = null,
)

/** Body for POST /api/v1/templates. */
data class EntityTemplateCreate(
    val name: String,
    val description: String? = null,
    val notes: String? = null,
    val defaultName: String? = null,
    val defaultDescription: String? = null,
    val defaultManufacturer: String? = null,
    val defaultModelNumber: String? = null,
    val defaultQuantity: Double? = null,
)

/** Body for PUT /api/v1/templates/{id}. The path id is authoritative server-side. */
data class EntityTemplateUpdate(
    val name: String,
    val description: String? = null,
    val notes: String? = null,
    val defaultName: String? = null,
    val defaultDescription: String? = null,
    val defaultManufacturer: String? = null,
    val defaultModelNumber: String? = null,
    val defaultQuantity: Double? = null,
)

/**
 * Body for POST /api/v1/templates/{id}/create-item. [parentId] (the containing location) is
 * required; the template's other defaults (manufacturer, model, warranty, custom fields,
 * quantity) are applied server-side.
 */
data class EntityTemplateCreateItem(
    val name: String,
    val parentId: String,
    val description: String? = null,
    val tagIds: List<String>? = null,
)
