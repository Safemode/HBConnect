package com.safemode.hbconnect.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One pending write in the offline outbox, persisted in Room so it survives a force-close. Rows are
 * replayed in insertion order ([createdAt] then [id]) when the server becomes reachable.
 *
 * [kind] is a [com.safemode.hbconnect.data.queue.OpKind] name and [payloadJson] is the Moshi-encoded
 * request body for that kind. [targetId] is the server id an update/delete acts on (always a real id:
 * an offline-created entity isn't reachable in the UI until it uploads, so no queued op ever depends
 * on another queued create - a create's own photos ride along in [photoPathsJson] instead).
 * [photoPathsJson] is a JSON array of file paths (copies kept in the app's files dir) for a create
 * that carries photos, or for a standalone attachment upload.
 *
 * [tenant] is the active collection id captured at enqueue time and [serverId] the active saved
 * server profile; the drainer only replays an op while that same server and collection are active,
 * so a queued write is never misrouted into another collection or onto another server. (Collection
 * ids alone aren't enough: the default collection is blank on every server.) [serverId] is null for
 * rows queued before it existed; those replay against whichever server is active.
 */
@Entity(tableName = "queued_op")
data class QueuedOpEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val tenant: String?,
    val payloadJson: String,
    val targetId: String?,
    val photoPathsJson: String?,
    val label: String,
    val createdAt: Long,
    val attempts: Int,
    val lastError: String?,
    val status: String,
    val serverId: String? = null,
)
