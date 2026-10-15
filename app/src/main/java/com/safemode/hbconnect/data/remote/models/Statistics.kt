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
 * A value total for one organizer (location or tag) — the summed purchase price of its items,
 * not an item count.
 */
data class TotalsByOrganizer(
    val id: String? = null,
    val name: String? = null,
    val total: Double = 0.0,
)

/**
 * Value-by-location and value-by-tag for the dashboard, computed client-side from non-archived
 * items. The server's /statistics/locations and /statistics/tags sum archived items too, which is
 * inconsistent with the archived-excluded totals shown everywhere else in the app.
 */
data class ValueBreakdowns(
    val byLocation: List<TotalsByOrganizer> = emptyList(),
    val byTag: List<TotalsByOrganizer> = emptyList(),
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
