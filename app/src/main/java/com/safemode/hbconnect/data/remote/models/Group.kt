package com.safemode.hbconnect.data.remote.models

/** The signed-in user's group (GET /api/v1/groups). [currency] is an ISO code like "USD". */
data class Group(
    val id: String? = null,
    val name: String? = null,
    val currency: String? = null,
)
