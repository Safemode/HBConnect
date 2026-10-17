package com.safemode.hbconnect.data.remote.models

/** The signed-in user's group (GET /api/v1/groups). [currency] is an ISO code like "USD". */
data class Group(
    val id: String? = null,
    val name: String? = null,
    val currency: String? = null,
)

/** Body for PUT /api/v1/groups. Both fields are written as-is, so send the current name. */
data class GroupUpdate(
    val name: String,
    val currency: String,
)

/** Body for POST /api/v1/groups - creates a new collection with the given name. */
data class GroupCreate(
    val name: String,
)
