package com.safemode.hbconnect.data.remote.models

/** Body for PUT /api/v1/users/self - updates the signed-in user's profile. */
data class UserUpdate(
    val name: String,
    val email: String,
)

/** Body for PUT /api/v1/users/self/change-password. */
data class ChangePassword(
    val current: String,
    val new: String,
)

/** An API key as listed by GET /api/v1/users/self/api-keys. The raw token is never returned here. */
data class ApiKeyOut(
    val id: String? = null,
    val name: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val lastUsedAt: String? = null,
)

/** Body for POST /api/v1/users/self/api-keys. [expiresAt] is ISO-8601; null means it never expires. */
data class ApiKeyCreate(
    val name: String,
    val expiresAt: String? = null,
)

/**
 * Response from POST /api/v1/users/self/api-keys. [token] is the raw key, returned only once at
 * creation and unrecoverable afterward, so it must be surfaced immediately.
 */
data class ApiKeyCreatedOut(
    val id: String? = null,
    val name: String? = null,
    val token: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
)
