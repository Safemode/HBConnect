package com.safemode.hbconnect.data.queue

import com.safemode.hbconnect.data.HomeboxRepository
import com.safemode.hbconnect.data.local.QueuedOpDao
import com.safemode.hbconnect.data.local.QueuedOpEntity
import com.safemode.hbconnect.data.remote.ServerStatus
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntityPatch
import com.safemode.hbconnect.data.remote.models.EntityTemplateCreate
import com.safemode.hbconnect.data.remote.models.EntityTemplateUpdate
import com.safemode.hbconnect.data.remote.models.EntityTypeCreate
import com.safemode.hbconnect.data.remote.models.EntityTypeUpdate
import com.safemode.hbconnect.data.remote.models.EntityUpdate
import com.safemode.hbconnect.data.remote.models.MaintenanceEntryRequest
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagUpdate
import com.safemode.hbconnect.notifications.QueueNotifier
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.util.UUID

/**
 * The offline write outbox. When the server is unreachable a write is persisted here instead of
 * failing; [drain] replays pending ops in order once the server returns. Rows live in Room so they
 * survive a force-close, and pending photos are copied into [queueDir] so they survive too.
 *
 * A drain only replays ops whose captured [QueuedOp.tenant] matches the collection that is active
 * now, so a write queued in one collection can never be sent into another after a switch. When a
 * drain actually flushed queued work, it posts a completion notification (if the user enabled it) -
 * never for ordinary online writes, which never enter the queue.
 */
class UploadQueueManager(
    private val dao: QueuedOpDao,
    private val repository: HomeboxRepository,
    private val queueDir: File,
    private val serverStatus: ServerStatus,
    private val isOnline: () -> Boolean,
    private val activeTenant: suspend () -> String,
    private val notificationsEnabled: suspend () -> Boolean,
    private val notifier: QueueNotifier,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val pathsAdapter: JsonAdapter<List<String>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, String::class.java))

    // Guards against two drains running at once (e.g. a reconnect and a manual retry overlapping).
    private val drainLock = Mutex()

    /** The pending outbox, newest-last, for the Queue screen. */
    val queue: Flow<List<QueuedOp>> = dao.observeAll().map { rows -> rows.map(::toDomain) }

    /** Count of pending ops, for a badge / glance. */
    val count: Flow<Int> = dao.observeCount()

    /** Outcome of [submit]: executed online, saved to the outbox, or failed against a reachable server. */
    sealed class Dispatch {
        object Sent : Dispatch()
        object Queued : Dispatch()
        data class Failed(val message: String?) : Dispatch()
    }

    /** True when a write should be attempted directly rather than queued. */
    fun online(): Boolean = isOnline() && serverStatus.isReachable()

    /**
     * Runs [direct] when the server is reachable, otherwise persists the write to the outbox. Used
     * for every write except the item-create flow (which needs the new id back on success and so
     * uses [enqueueCreateEntity] on its own offline branch).
     */
    suspend fun submit(
        kind: OpKind,
        payload: Any?,
        targetId: String? = null,
        photoSources: List<String> = emptyList(),
        label: String,
        direct: suspend () -> Result<*>,
    ): Dispatch {
        if (online()) {
            val r = direct()
            return if (r.isSuccess) Dispatch.Sent else Dispatch.Failed(r.exceptionOrNull()?.message)
        }
        persist(kind, payload, targetId, photoSources, label)
        return Dispatch.Queued
    }

    /**
     * Queues a new entity together with any photos. [body] is an [EntityUpdate] with a blank id that
     * carries the full set of fields the form captured; on replay it is turned into a create and then
     * an update, so detail the create endpoint can't accept (serial, notes, purchase, warranty...) is
     * not lost offline.
     */
    suspend fun enqueueCreateEntity(body: EntityUpdate, photoSources: List<String>, label: String) {
        persist(OpKind.CREATE_ENTITY, body, targetId = null, photoSources = photoSources, label = label)
    }

    /** Unconditionally saves a write to the outbox (used by offline-only branches that already know
     *  the server is unreachable, so there is no online path to try). */
    suspend fun enqueue(kind: OpKind, payload: Any?, targetId: String? = null, label: String) {
        persist(kind, payload, targetId, emptyList(), label)
    }

    /** Queues photos (file paths) to be attached to an existing entity. */
    suspend fun enqueueUploadPhotos(entityId: String, paths: List<String>, label: String) {
        if (paths.isEmpty()) return
        persist(OpKind.UPLOAD_ATTACHMENT, AttachmentUploadPayload(type = "photo"), targetId = entityId, photoSources = paths, label = label)
    }

    /** Queues a single attachment (given its bytes) for an existing entity; the bytes are saved to disk. */
    suspend fun enqueueUploadBytes(
        entityId: String,
        fileName: String,
        mimeType: String?,
        type: String,
        bytes: ByteArray,
        label: String,
    ) {
        if (!queueDir.exists()) queueDir.mkdirs()
        val tempId = UUID.randomUUID().toString()
        val file = File(queueDir, "pending_$tempId")
        runCatching { file.writeBytes(bytes) }.getOrElse { return }
        persist(
            OpKind.UPLOAD_ATTACHMENT,
            AttachmentUploadPayload(type = type, fileName = fileName, mimeType = mimeType),
            targetId = entityId,
            photoSources = listOf(file.absolutePath),
            label = label,
        )
        // persist() copies the sources into the queue dir, so drop our staging copy.
        runCatching { file.delete() }
    }

    /** Drops a queued op and deletes any photo copies it owned. */
    suspend fun remove(opId: String) {
        dao.getAll().firstOrNull { it.id == opId }?.let { deletePhotos(toDomain(it)) }
        dao.delete(opId)
    }

    /**
     * Replays everything for the active collection that can be sent now. Each op that succeeds is
     * removed (and its photos deleted); one that fails against a reachable server is marked FAILED
     * with its error and left for the user to retry or remove, so a single bad op can't wedge the
     * queue. If the server drops mid-drain, the pass stops and the rest stay pending.
     */
    suspend fun drain() {
        if (!online() || !drainLock.tryLock()) return
        try {
            val tenant = activeTenant()
            var uploaded = 0
            var failed = 0
            for (row in dao.getAll()) {
                if (!online()) break
                if (row.tenant.orEmpty() != tenant) continue
                val op = toDomain(row)
                val result = replay(op)
                if (result.isSuccess) {
                    deletePhotos(op)
                    dao.delete(op.id)
                    uploaded++
                } else {
                    dao.upsert(
                        row.copy(
                            attempts = row.attempts + 1,
                            lastError = result.exceptionOrNull()?.message ?: "Upload failed.",
                            status = QueuedOp.Status.FAILED.name,
                        ),
                    )
                    failed++
                }
            }
            if ((uploaded > 0 || failed > 0) && notificationsEnabled()) {
                notifier.notifyDrainComplete(uploaded = uploaded, failed = failed)
            }
        } finally {
            drainLock.unlock()
        }
    }

    private suspend fun replay(op: QueuedOp): Result<Unit> = when (op.kind) {
        OpKind.CREATE_ENTITY -> createEntityWithPhotos(op)
        OpKind.UPDATE_ENTITY ->
            repository.updateEntity(op.targetId.orEmpty(), decode(op.payloadJson, EntityUpdate::class.java)).map {}
        OpKind.PATCH_ENTITY ->
            repository.patchEntity(op.targetId.orEmpty(), decode(op.payloadJson, EntityPatch::class.java)).map {}
        OpKind.DELETE_ENTITY -> repository.deleteEntity(op.targetId.orEmpty())
        OpKind.UPLOAD_ATTACHMENT -> uploadAttachment(op)
        OpKind.DELETE_ATTACHMENT -> {
            val ref = decode(op.payloadJson, AttachmentRef::class.java)
            repository.deleteAttachment(ref.entityId, ref.attachmentId)
        }
        OpKind.CREATE_TAG -> repository.createTag(decode(op.payloadJson, TagCreate::class.java)).map {}
        OpKind.UPDATE_TAG ->
            repository.updateTag(op.targetId.orEmpty(), decode(op.payloadJson, TagUpdate::class.java)).map {}
        OpKind.DELETE_TAG -> repository.deleteTag(op.targetId.orEmpty())
        OpKind.CREATE_TYPE -> repository.createEntityType(decode(op.payloadJson, EntityTypeCreate::class.java)).map {}
        OpKind.UPDATE_TYPE ->
            repository.updateEntityType(op.targetId.orEmpty(), decode(op.payloadJson, EntityTypeUpdate::class.java)).map {}
        OpKind.DELETE_TYPE -> repository.deleteEntityType(op.targetId.orEmpty())
        OpKind.CREATE_TEMPLATE ->
            repository.createTemplate(decode(op.payloadJson, EntityTemplateCreate::class.java)).map {}
        OpKind.UPDATE_TEMPLATE ->
            repository.updateTemplate(op.targetId.orEmpty(), decode(op.payloadJson, EntityTemplateUpdate::class.java)).map {}
        OpKind.DELETE_TEMPLATE -> repository.deleteTemplate(op.targetId.orEmpty())
        OpKind.CREATE_MAINTENANCE ->
            repository.createMaintenance(op.targetId.orEmpty(), decode(op.payloadJson, MaintenanceEntryRequest::class.java))
        OpKind.UPDATE_MAINTENANCE ->
            repository.updateMaintenance(op.targetId.orEmpty(), decode(op.payloadJson, MaintenanceEntryRequest::class.java))
        OpKind.DELETE_MAINTENANCE -> repository.deleteMaintenance(op.targetId.orEmpty())
    }

    /**
     * Recreates the entity from the queued [EntityUpdate]: a create carries the fields the create
     * endpoint accepts, then a follow-up update applies the rest, then each photo is uploaded (the
     * first as the primary image). Mirrors the online new-item path so nothing entered offline is lost.
     */
    private suspend fun createEntityWithPhotos(op: QueuedOp): Result<Unit> {
        val update = decode(op.payloadJson, EntityUpdate::class.java)
        val create = EntityCreate(
            name = update.name,
            description = update.description,
            entityTypeId = update.entityTypeId,
            parentId = update.parentId,
            quantity = update.quantity,
            tagIds = update.tagIds,
            manufacturer = update.manufacturer,
            modelNumber = update.modelNumber,
        )
        val entity = repository.createEntity(create).getOrElse { return Result.failure(it) }
        val id = entity.id ?: return Result.success(Unit)
        repository.updateEntity(id, update.copy(id = id)).onFailure { return Result.failure(it) }
        var firstError: Throwable? = null
        op.photoPaths.forEachIndexed { index, path ->
            val file = File(path)
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@forEachIndexed
            repository.uploadAttachment(
                entityId = id,
                fileName = file.name,
                mimeType = "image/jpeg",
                bytes = bytes,
                type = "photo",
                primary = index == 0,
            ).onFailure { if (firstError == null) firstError = it }
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    /** Uploads each queued file to the target entity, deriving name/type per file when not pinned. */
    private suspend fun uploadAttachment(op: QueuedOp): Result<Unit> {
        if (op.photoPaths.isEmpty()) return Result.failure(IllegalStateException("The queued file is missing."))
        val payload = decode(op.payloadJson, AttachmentUploadPayload::class.java)
        var firstError: Throwable? = null
        op.photoPaths.forEach { path ->
            val file = File(path)
            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes == null) {
                if (firstError == null) firstError = IllegalStateException("Couldn't read a queued file.")
                return@forEach
            }
            repository.uploadAttachment(
                entityId = op.targetId.orEmpty(),
                fileName = payload.fileName ?: file.name,
                mimeType = payload.mimeType ?: "image/jpeg",
                bytes = bytes,
                type = payload.type,
            ).onFailure { if (firstError == null) firstError = it }
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    // ---- persistence + mapping ----

    private suspend fun persist(
        kind: OpKind,
        payload: Any?,
        targetId: String?,
        photoSources: List<String>,
        label: String,
    ) {
        val opId = UUID.randomUUID().toString()
        val copied = photoSources.mapIndexedNotNull { index, src -> copyIntoQueueDir(src, opId, index) }
        dao.upsert(
            QueuedOpEntity(
                id = opId,
                kind = kind.name,
                tenant = activeTenant(),
                payloadJson = payload?.let(::encode).orEmpty(),
                targetId = targetId,
                photoPathsJson = copied.takeIf { it.isNotEmpty() }?.let { pathsAdapter.toJson(it) },
                label = label,
                createdAt = System.currentTimeMillis(),
                attempts = 0,
                lastError = null,
                status = QueuedOp.Status.PENDING.name,
            ),
        )
    }

    private fun encode(payload: Any): String {
        @Suppress("UNCHECKED_CAST")
        val adapter = moshi.adapter(payload.javaClass) as JsonAdapter<Any>
        return adapter.toJson(payload)
    }

    private fun <T> decode(json: String, clazz: Class<T>): T =
        moshi.adapter(clazz).fromJson(json) ?: throw IllegalStateException("Corrupt queued payload.")

    /** Copies a pending photo into the durable queue dir so it survives even if the original is cleared. */
    private fun copyIntoQueueDir(sourcePath: String, opId: String, index: Int): String? {
        val source = File(sourcePath)
        if (!source.exists()) return null
        if (!queueDir.exists()) queueDir.mkdirs()
        val ext = source.extension.ifBlank { "jpg" }
        val dest = File(queueDir, "${opId}_$index.$ext")
        return runCatching { source.copyTo(dest, overwrite = true).absolutePath }.getOrNull()
    }

    private fun deletePhotos(op: QueuedOp) {
        op.photoPaths.forEach { runCatching { File(it).delete() } }
    }

    private fun toDomain(e: QueuedOpEntity): QueuedOp = QueuedOp(
        id = e.id,
        kind = runCatching { OpKind.valueOf(e.kind) }.getOrDefault(OpKind.UPDATE_ENTITY),
        tenant = e.tenant,
        payloadJson = e.payloadJson,
        targetId = e.targetId,
        photoPaths = e.photoPathsJson?.let { runCatching { pathsAdapter.fromJson(it) }.getOrNull() }.orEmpty(),
        label = e.label,
        createdAt = e.createdAt,
        attempts = e.attempts,
        lastError = e.lastError,
        status = runCatching { QueuedOp.Status.valueOf(e.status) }.getOrDefault(QueuedOp.Status.PENDING),
    )
}
