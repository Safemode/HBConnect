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
 * A pending write held in the outbox, as the UI and drainer see it (the in-memory form of
 * [com.safemode.hbconnect.data.local.QueuedOpEntity]).
 */
data class QueuedOp(
    val id: String,
    val kind: OpKind,
    val tenant: String?,
    /** The saved server profile this op was queued against; null for ops queued before servers were tracked. */
    val serverId: String?,
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
