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

/**
 * GET /api/v1/groups/statistics/locations and /api/v1/groups/statistics/tags.
 * [total] is the summed purchase price of the organizer's items, not an item count.
 */
data class TotalsByOrganizer(
    val id: String? = null,
    val name: String? = null,
    val total: Double = 0.0,
)

/** A single priced item's contribution, keyed by its creation date (RFC 3339). */
data class ValueOverTimeEntry(
    val date: String? = null,
    val value: Double = 0.0,
    val name: String? = null,
)

/**
 * GET /api/v1/groups/statistics/purchase-price. [entries] are the individual priced items
 * created within the requested window; a cumulative value line starts from [valueAtStart]
 * and ends at [valueAtEnd].
 */
data class ValueOverTime(
    val valueAtStart: Double = 0.0,
    val valueAtEnd: Double = 0.0,
    val start: String? = null,
    val end: String? = null,
    val entries: List<ValueOverTimeEntry> = emptyList(),
)
