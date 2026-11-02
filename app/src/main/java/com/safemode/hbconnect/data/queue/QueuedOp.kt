package com.safemode.hbconnect.data.queue

/**
 * The kinds of write the offline outbox can hold and replay. Each maps to one
 * [com.safemode.hbconnect.data.HomeboxRepository] call in [UploadQueueManager.replay]. Only
 * inventory-data mutations are queueable; auth, account, membership, group-settings and
 * import/backup operations are deliberately kept online-only (they need live server validation).
 */
enum class OpKind {
    CREATE_ENTITY, UPDATE_ENTITY, PATCH_ENTITY, DELETE_ENTITY,
    UPLOAD_ATTACHMENT, DELETE_ATTACHMENT,
    CREATE_TAG, UPDATE_TAG, DELETE_TAG,
    CREATE_TYPE, UPDATE_TYPE, DELETE_TYPE,
    CREATE_TEMPLATE, UPDATE_TEMPLATE, DELETE_TEMPLATE,
    CREATE_MAINTENANCE, UPDATE_MAINTENANCE, DELETE_MAINTENANCE,

    /**
     * An edit to an existing entity, stored as an [com.safemode.hbconnect.data.EntityEditPayload]
     * and merged onto the server's current version on replay, so changes made elsewhere while this
     * device was offline survive. (UPDATE_ENTITY is a blind full replace, kept for older rows and for
     * the follow-up update of a queued create.)
     */
    MERGE_ENTITY,
}

/**
 * Payload for [OpKind.UPLOAD_ATTACHMENT]; the files themselves travel in the op's photo paths (one
 * op can carry several, e.g. all the photos taken for an edited item). [fileName]/[mimeType] are set
 * for a single picked document and left null for item photos, which derive both per file on replay.
 * [primaryFirst] makes the first file that uploads the entity's primary image (used for the photos
 * of a new item, or an item that has none yet); it is cleared once that upload succeeds.
 */
data class AttachmentUploadPayload(
    val type: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val primaryFirst: Boolean = false,
)

/** Payload for [OpKind.DELETE_ATTACHMENT]. */
data class AttachmentRef(
    val entityId: String,
    val attachmentId: String,
)

/**
 * Where queued writes would be sent right now: the active saved [server], its [tenant] (collection,
 * blank for the default) and the [account] signed in there (blank until known). [ready] is false
 * while there is nothing to send with, or the API client hasn't caught up with a switch yet.
 */
data class QueueTarget(
    val server: String,
    val tenant: String,
    val account: String,
    val ready: Boolean = true,
) {
    /** Same server, collection and account as [other] (ignoring [ready]). */
    fun sameScope(other: QueueTarget): Boolean =
        server == other.server && tenant == other.tenant && account == other.account

    /**
     * True when [op] would be replayed here: same collection, the same server (ops queued before
     * servers were tracked have none and follow the active one), and the same account (ops queued
     * before accounts were tracked, or while the account wasn't known yet, have none).
     */
    fun accepts(op: QueuedOp): Boolean =
        (op.serverId == null || op.serverId == server) &&
            op.tenant.orEmpty() == tenant &&
            (op.accountId == null || op.accountId == account)
}

/**
 * A pending write held in the outbox, as the UI and drainer see it (the in-memory form of
 * [com.safemode.hbconnect.data.local.QueuedOpEntity]).
 */
data class QueuedOp(
    val id: String,
    val kind: OpKind,
    val tenant: String?,
    /** The saved server profile this op was queued against; null for ops queued before servers were tracked. */
    val serverId: String?,
    /** The account signed in when this op was queued; null when unknown or for older ops. */
    val accountId: String? = null,
    val payloadJson: String,
    val targetId: String?,
    val photoPaths: List<String>,
    val label: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val status: Status = Status.PENDING,
) {
    enum class Status { PENDING, FAILED }
}
