package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.remote.models.EntityPath

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
