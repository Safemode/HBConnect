package com.safemode.hbconnect.data.settings

/** The tiles the home dashboard can show, in their default order. */
enum class DashboardTileId {
    ITEMS,
    LOCATIONS,
    TAGS,
    WARRANTY,
    VALUE,
    RECENT,
}

/** Smallest and largest number of items the "Recently added" tile can list. */
const val MIN_RECENT_ROWS = 1
const val MAX_RECENT_ROWS = 12

/**
 * One tile's placement on the dashboard: whether it shows, whether it spans the full width (a single
 * column) or sits half-width beside a neighbour, and - for [DashboardTileId.RECENT] only - how many
 * items it lists. [rows] is ignored for the stat tiles.
 */
data class DashboardTilePref(
    val id: DashboardTileId,
    val visible: Boolean = true,
    val fullWidth: Boolean = false,
    val rows: Int = 3,
)

/**
 * The out-of-the-box layout: the five stat tiles two-up, then a full-width "Recently added" card.
 * Also the source of truth for which tiles exist, so [decodeDashboardLayout] can add any tile a
 * newer build introduces and drop ones it no longer knows.
 */
fun defaultDashboardLayout(): List<DashboardTilePref> = listOf(
    DashboardTilePref(DashboardTileId.ITEMS),
    DashboardTilePref(DashboardTileId.LOCATIONS),
    DashboardTilePref(DashboardTileId.TAGS),
    DashboardTilePref(DashboardTileId.WARRANTY),
    DashboardTilePref(DashboardTileId.VALUE),
    DashboardTilePref(DashboardTileId.RECENT, fullWidth = true, rows = 3),
)

/** Serializes a layout to a single preference string: `ITEMS:1:0:3|LOCATIONS:1:0:3|...`. */
fun encodeDashboardLayout(layout: List<DashboardTilePref>): String =
    layout.joinToString("|") { pref ->
        val v = if (pref.visible) 1 else 0
        val f = if (pref.fullWidth) 1 else 0
        "${pref.id.name}:$v:$f:${pref.rows}"
    }

/**
 * Parses a layout string back into prefs, merged against [defaultDashboardLayout]: tiles the string
 * lists keep their saved order and settings, any default tile the string is missing is appended, and
 * unknown ids are ignored. A blank or unparseable string falls back to the default order.
 */
fun decodeDashboardLayout(raw: String?): List<DashboardTilePref> {
    val defaults = defaultDashboardLayout()
    if (raw.isNullOrBlank()) return defaults
    val parsed = raw.split("|").mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size < 4) return@mapNotNull null
        val id = runCatching { DashboardTileId.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
        DashboardTilePref(
            id = id,
            visible = parts[1] == "1",
            fullWidth = parts[2] == "1",
            rows = parts[3].toIntOrNull()?.coerceIn(MIN_RECENT_ROWS, MAX_RECENT_ROWS) ?: 3,
        )
    }
    if (parsed.isEmpty()) return defaults
    // Keep one entry per tile (first wins), then append any tile the saved string didn't cover.
    val seen = LinkedHashMap<DashboardTileId, DashboardTilePref>()
    parsed.forEach { pref -> seen.putIfAbsent(pref.id, pref) }
    defaults.forEach { pref -> seen.putIfAbsent(pref.id, pref) }
    return seen.values.toList()
}
