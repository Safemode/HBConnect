package com.safemode.hbconnect.data.remote.models

/** A tag (GET /api/v1/tags). Tags replaced labels in Homebox v0.24+. */
data class TagSummary(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val parentId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Body for POST /api/v1/tags */
data class TagCreate(
    val name: String,
    val description: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val parentId: String? = null,
)

/** Body for PUT /api/v1/tags/{id} */
data class TagUpdate(
    val name: String,
    val description: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val parentId: String? = null,
)

/** GET/PUT /api/v1/tags/{id} */
data class TagOut(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val parentId: String? = null,
    val children: List<TagSummary>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
