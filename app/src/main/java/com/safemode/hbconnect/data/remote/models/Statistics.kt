package com.safemode.hbconnect.data.remote.models

/** GET /api/v1/groups/statistics */
data class GroupStatistics(
    val totalItems: Int? = null,
    val totalLocations: Int? = null,
    val totalTags: Int? = null,
    val totalUsers: Int? = null,
    val totalWithWarranty: Int? = null,
    val totalItemPrice: Double? = null,
)
