package com.safemode.hbconnect.ui.scan

/**
 * Parsing for the codes printed on Homebox labels, shared by the dashboard's one-shot scanner and the
 * AR overlay scanner so both resolve a scanned code the same way.
 *
 * A printed label encodes the entity's web URL. Item/location labels use `.../item/{uuid}` or
 * `.../location/{uuid}` (we lift the UUID out), while an asset label uses `.../a/{assetId}` (e.g.
 * 000-001), which has to be resolved to its item by an asset-tag lookup. The host is ignored on
 * purpose, so a label pointing at a public hostname still resolves on a LAN connection. A bare UUID
 * or asset tag typed/encoded on its own is accepted too.
 */

/** A UUID anywhere in the scanned text. */
private val UUID_REGEX =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** The `.../a/{assetId}` segment of an asset label's URL; the id is digits, often grouped (000-001). */
private val ASSET_PATH_REGEX = Regex("/a/([0-9][0-9-]*)")

/** A bare asset tag on its own, e.g. "000-001" or "42". */
private val ASSET_BARE_REGEX = Regex("[0-9][0-9-]*")

/** What a scanned Homebox label resolves to. */
sealed interface ScanTarget {
    /** An item or location, opened directly by its UUID. */
    data class Entity(val id: String) : ScanTarget

    /** An asset tag, resolved to its item via a lookup. */
    data class Asset(val assetId: String) : ScanTarget
}

/**
 * Resolves a scanned Homebox label, or null if the code isn't one. The UUID is checked first so an
 * item/location URL never falls through to the asset branch (those URLs also contain digits).
 */
fun parseHomeboxLabel(raw: String): ScanTarget? {
    val s = raw.trim()
    UUID_REGEX.find(s)?.let { return ScanTarget.Entity(it.value) }
    ASSET_PATH_REGEX.find(s)?.let { return ScanTarget.Asset(it.groupValues[1]) }
    if (s.matches(ASSET_BARE_REGEX)) return ScanTarget.Asset(s)
    return null
}
