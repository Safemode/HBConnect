package com.safemode.hbconnect.data

/** A custom-field match: entities whose custom field [name] equals [value]. */
data class FieldFilter(val name: String, val value: String) {
    /** The `fields` query-param form Homebox expects: `Name=Value`. */
    fun toParam(): String = "$name=$value"
}

/** Photo presence filter for a search. */
enum class PhotoFilter { ANY, WITH, WITHOUT }

/** Result ordering for a search. [orderBy]/[orderDirection] match the entities endpoint params. */
enum class SearchSort(val label: String, val orderBy: String, val orderDirection: String) {
    NAME("Name", "name", "asc"),
    CREATED("Created at", "createdAt", "desc"),
    UPDATED("Updated at", "updatedAt", "desc"),
}

/**
 * Optional filters applied to the dashboard's server-wide entity search. All are additive (AND),
 * except [tagIds] which the server ORs together — unless [negateTags] is set, which excludes
 * entities carrying any selected tag.
 */
data class EntityFilters(
    /** Selected location (parent) ids to restrict results to. */
    val locationIds: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val includeArchived: Boolean = false,
    val negateTags: Boolean = false,
    val photo: PhotoFilter = PhotoFilter.ANY,
    val fields: List<FieldFilter> = emptyList(),
    /** Result ordering — not counted as an active filter (it reorders, it doesn't restrict). */
    val sort: SearchSort = SearchSort.NAME,
) {
    /** Number of active filter facets, for the filter-button badge. */
    val activeCount: Int
        get() = locationIds.size + tagIds.size + fields.size +
            (if (includeArchived) 1 else 0) +
            (if (negateTags) 1 else 0) +
            (if (photo != PhotoFilter.ANY) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}
