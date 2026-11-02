package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.remote.models.EntityPath
import com.safemode.hbconnect.data.remote.models.EntitySummary

/** True when this path node is a location: by its type, or by being one of [knownLocationIds]. */
fun EntityPath.isLocation(knownLocationIds: Set<String> = emptySet()): Boolean =
    type.equals("location", ignoreCase = true) || (id != null && id in knownLocationIds)

/**
 * The closest location above the entity a path leads to. The path is root-first and ends with the
 * entity itself, which is not considered.
 */
fun List<EntityPath>.nearestLocation(knownLocationIds: Set<String> = emptySet()): EntityPath? =
    dropLast(1).lastOrNull { it.isLocation(knownLocationIds) }

/**
 * True when the entity this path leads to is [id] or sits anywhere inside it. Used to stop an item
 * from being put inside itself or one of its own sub-items.
 */
fun List<EntityPath>.isWithin(id: String): Boolean = any { it.id == id }

/**
 * The location this item is in, following parent items up through [itemsById] (the collection's
 * items by id) until reaching one that isn't an item. A parent missing from [itemsById] (archived,
 * say) is taken as the location unless it is known to be an item. Stops after a bounded number of
 * hops so a malformed cycle can't loop forever.
 */
fun EntitySummary.locationVia(itemsById: Map<String?, EntitySummary>): EntitySummary? {
    var node = parent
    repeat(MAX_PARENT_HOPS) {
        val current = node ?: return null
        if (current.entityType?.isLocation == true) return current
        val asItem = itemsById[current.id] ?: return current.takeIf { it.entityType?.isLocation != false }
        node = asItem.parent
    }
    return null
}

private const val MAX_PARENT_HOPS = 64
