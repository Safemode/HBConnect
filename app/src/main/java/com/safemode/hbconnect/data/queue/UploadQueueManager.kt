package com.safemode.hbconnect.data.queue

import com.safemode.hbconnect.data.EntityEditPayload
import com.safemode.hbconnect.data.HomeboxRepository
import com.safemode.hbconnect.data.imageMimeType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The offline write outbox. When the server is unreachable a write is persisted here instead of
 * failing; [drain] replays pending ops in order once the server returns. Rows live in Room so they
 * survive a force-close, and pending photos are copied into [queueDir] so they survive too.
 *
 * A drain only replays ops whose captured server ([QueuedOp.serverId]) and collection
 * ([QueuedOp.tenant]) match the ones active now, so a write queued in one place can never be sent
 * somewhere else after a switch. When a drain actually flushed queued work, it posts a completion
 * notification (if the user enabled it) - never for ordinary online writes, which never enter the
 * queue.
 *
 * Replays are safe to repeat: progress is written back as soon as the server accepts each step, so
 * a retry after a partial failure resumes where it stopped instead of redoing work (see
 * [createEntity] and [uploadFiles]). Each op also runs to completion even if the drain is cancelled
 * (the app going to the background), so a write the server applied is always recorded as done.
 */
class UploadQueueManager(
    private val dao: QueuedOpDao,
    private val repository: HomeboxRepository,
    private val queueDir: File,
    private val serverStatus: ServerStatus,
    private val isOnline: () -> Boolean,
    private val activeServer: suspend () -> String,
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
     * True when [op] would be replayed against [server] and [tenant]: same collection, and the same
     * server (ops queued before servers were tracked have no server and follow the active one).
     */
    fun belongsTo(op: QueuedOp, server: String, tenant: String): Boolean =
        (op.serverId == null || op.serverId == server) && op.tenant.orEmpty() == tenant

    /**
     * Runs [direct] when the server is reachable, otherwise persists the write to the outbox. Used
     * for every write except the item-create flow (which needs the new id back on success and so
     * uses [enqueueCreateEntity] on its own offline branch). If [direct] fails because the
     * connection dropped before the request reached the server ([isUndelivered]), the write is
     * queued too, rather than reported as a failure.
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
            if (r.isSuccess) return Dispatch.Sent
            if (!isUndelivered(r.exceptionOrNull())) return Dispatch.Failed(r.exceptionOrNull()?.message)
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

    /**
     * Queues photos (file paths) to be attached to an existing entity. [primaryFirst] makes the first
     * one that uploads the entity's primary image (for an entity that has none yet).
     */
    suspend fun enqueueUploadPhotos(
        entityId: String,
        paths: List<String>,
        label: String,
        primaryFirst: Boolean = false,
    ) {
        if (paths.isEmpty()) return
        persist(
            OpKind.UPLOAD_ATTACHMENT,
            AttachmentUploadPayload(type = "photo", primaryFirst = primaryFirst),
            targetId = entityId,
            photoSources = paths,
            label = label,
        )
    }

    /**
     * Queues a single attachment ([file] on disk) for an existing entity. The file is copied into the
     * queue dir, so the caller may delete its own copy afterwards. [primary] makes it the entity's
     * primary image once it uploads.
     */
    suspend fun enqueueUploadFile(
        entityId: String,
        file: File,
        fileName: String,
        mimeType: String?,
        type: String,
        label: String,
        primary: Boolean = false,
    ) {
        persist(
            OpKind.UPLOAD_ATTACHMENT,
            AttachmentUploadPayload(type = type, fileName = fileName, mimeType = mimeType, primaryFirst = primary),
            targetId = entityId,
            photoSources = listOf(file.absolutePath),
            label = label,
        )
    }

    /** Drops a queued op and deletes any photo copies it owned. */
    suspend fun remove(opId: String) {
        dao.getAll().firstOrNull { it.id == opId }?.let { deletePhotos(toDomain(it)) }
        dao.delete(opId)
    }

    /**
     * Drops every op queued against a saved server, with its photo copies. Used when that server is
     * deleted: its writes can never be sent anywhere else.
     */
    suspend fun removeForServer(serverId: String) {
        if (serverId.isBlank()) return
        dao.forServer(serverId).forEach { row ->
            deletePhotos(toDomain(row))
            dao.delete(row.id)
        }
    }

    /**
     * Deletes files in [queueDir] that no queued op references - left behind when the app died
     * mid-write, or by an older build that dropped queue rows on a database upgrade. Files changed in
     * the last hour are skipped, so a copy made for an op that is being saved right now is safe.
     */
    suspend fun sweepOrphanFiles() {
        val files = queueDir.listFiles() ?: return
        val referenced = dao.getAll()
            .flatMap { toDomain(it).photoPaths }
            .map { File(it).absolutePath }
            .toSet()
        val cutoff = System.currentTimeMillis() - ORPHAN_MIN_AGE_MS
        files.filter { it.isFile && it.absolutePath !in referenced && it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
    }

    /**
     * Replays everything for the active server and collection that can be sent now. Each op that
     * succeeds is removed (and its photos deleted); one that fails against a reachable server is
     * marked FAILED with its error and retried on the next drain (or removed by the user), so a single
     * bad op can't wedge the queue. If the server drops mid-drain, the pass stops and the rest stay
     * pending.
     */
    suspend fun drain() {
        if (!online() || !drainLock.tryLock()) return
        try {
            val server = activeServer()
            val tenant = activeTenant()
            var uploaded = 0
            var failed = 0
            for (row in dao.getAll()) {
                // Stop between ops if the drain was cancelled; an op already started finishes below.
                currentCoroutineContext().ensureActive()
                if (!online()) break
                if (!belongsTo(toDomain(row), server, tenant)) continue
                val ok = withContext(NonCancellable) {
                    try {
                        process(row)
                    } catch (e: Exception) {
                        // E.g. a payload that no longer decodes: record it rather than crash the drain.
                        markFailed(row, e)
                        false
                    }
                }
                if (ok) uploaded++ else failed++
            }
            if ((uploaded > 0 || failed > 0) && notificationsEnabled()) {
                notifier.notifyDrainComplete(uploaded = uploaded, failed = failed)
            }
        } finally {
            drainLock.unlock()
        }
    }

    /** Replays one queued op, recording the outcome. Returns true when it fully succeeded. */
    private suspend fun process(row: QueuedOpEntity): Boolean {
        val op = toDomain(row)
        return when (op.kind) {
            OpKind.CREATE_ENTITY -> createEntity(row, op)
            OpKind.UPLOAD_ATTACHMENT -> uploadFiles(row, op)
            else -> finish(row, replay(op))
        }
    }

    /** Removes a succeeded op (and its photos), or marks a failed one. Returns whether it succeeded. */
    private suspend fun finish(row: QueuedOpEntity, result: Result<*>): Boolean {
        if (result.isSuccess) {
            deletePhotos(toDomain(row))
            dao.delete(row.id)
            return true
        }
        markFailed(row, result.exceptionOrNull())
        return false
    }

    private suspend fun markFailed(row: QueuedOpEntity, error: Throwable?) {
        dao.upsert(
            row.copy(
                attempts = row.attempts + 1,
                lastError = error?.message ?: "Upload failed.",
                status = QueuedOp.Status.FAILED.name,
            ),
        )
    }

    /** The single-request ops. Creates and file uploads are multi-step and handled separately. */
    private suspend fun replay(op: QueuedOp): Result<Unit> = when (op.kind) {
        OpKind.UPDATE_ENTITY ->
            repository.updateEntity(op.targetId.orEmpty(), decode(op.payloadJson, EntityUpdate::class.java)).map {}
        OpKind.MERGE_ENTITY -> {
            val edit = decode(op.payloadJson, EntityEditPayload::class.java)
            repository.mergeUpdateEntity(op.targetId.orEmpty(), edit.base, edit.edited).map {}
        }
        OpKind.PATCH_ENTITY ->
            repository.patchEntity(op.targetId.orEmpty(), decode(op.payloadJson, EntityPatch::class.java)).map {}
        OpKind.DELETE_ENTITY -> repository.deleteEntity(op.targetId.orEmpty())
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
        OpKind.CREATE_ENTITY, OpKind.UPLOAD_ATTACHMENT ->
            Result.failure(IllegalStateException("${op.kind} is not a single-request op."))
    }

    /**
     * Recreates the entity from the queued [EntityUpdate]: a create carries the fields the create
     * endpoint accepts, then a follow-up update applies the rest, then each photo is uploaded (the
     * first as the primary image). Mirrors the online new-item path so nothing entered offline is lost.
     *
     * As soon as the create succeeds, this op is rewritten in place as an update aimed at the new id,
     * plus a separate photo-upload op, in one transaction. A later failure then retries only those
     * follow-up steps; it never creates the entity a second time.
     */
    private suspend fun createEntity(row: QueuedOpEntity, op: QueuedOp): Boolean {
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
        val entity = repository.createEntity(create).getOrElse { return finish(row, Result.failure<Unit>(it)) }
        val id = entity.id ?: return finish(row, Result.success(Unit))

        val fullUpdate = update.copy(id = id)
        val updateRow = row.copy(
            kind = OpKind.UPDATE_ENTITY.name,
            targetId = id,
            payloadJson = encode(fullUpdate),
            photoPathsJson = null,
        )
        val photoRow = row.photoPathsJson?.takeIf { op.photoPaths.isNotEmpty() }?.let { paths ->
            row.copy(
                id = UUID.randomUUID().toString(),
                kind = OpKind.UPLOAD_ATTACHMENT.name,
                targetId = id,
                payloadJson = encode(AttachmentUploadPayload(type = "photo", primaryFirst = true)),
                photoPathsJson = paths,
                label = "Photos for ${update.name}",
            )
        }
        dao.upsertAll(listOfNotNull(updateRow, photoRow))

        val updated = finish(updateRow, repository.updateEntity(id, fullUpdate))
        val photosUploaded = photoRow?.let { uploadFiles(it, toDomain(it)) } ?: true
        return updated && photosUploaded
    }

    /**
     * Uploads each queued file to the target entity, deriving name/type per file when not pinned.
     * Each file is deleted and dropped from the op as soon as it uploads, so a retry only sends the
     * files that are still outstanding. A file that has gone missing from disk can never upload, so it
     * is dropped (and reported) rather than failing the op forever.
     */
    private suspend fun uploadFiles(row: QueuedOpEntity, op: QueuedOp): Boolean {
        if (op.photoPaths.isEmpty()) {
            return finish(row, Result.failure<Unit>(IllegalStateException("The queued file is missing.")))
        }
        var payload = decode(op.payloadJson, AttachmentUploadPayload::class.java)
        val remaining = op.photoPaths.toMutableList()
        var current = row
        var firstError: Throwable? = null

        fun snapshot(): QueuedOpEntity = current.copy(
            payloadJson = encode(payload),
            photoPathsJson = remaining.takeIf { it.isNotEmpty() }?.let { pathsAdapter.toJson(it) },
        )

        for (path in op.photoPaths) {
            val file = File(path)
            if (!file.isFile) {
                remaining -= path
                if (firstError == null) firstError = IllegalStateException("A queued file was missing.")
                continue
            }
            val result = repository.uploadAttachment(
                entityId = op.targetId.orEmpty(),
                file = file,
                fileName = payload.fileName ?: file.name,
                mimeType = payload.mimeType ?: imageMimeType(file.name),
                type = payload.type,
                primary = payload.primaryFirst,
            )
            if (result.isSuccess) {
                runCatching { file.delete() }
                remaining -= path
                if (payload.primaryFirst) payload = payload.copy(primaryFirst = false)
                current = snapshot()
                if (remaining.isNotEmpty()) dao.upsert(current)
            } else if (firstError == null) {
                firstError = result.exceptionOrNull()
            }
        }

        val error = firstError
        return when {
            error == null -> {
                dao.delete(row.id)
                true
            }
            remaining.isEmpty() -> {
                // Everything that could upload did; only missing files were left behind.
                dao.delete(row.id)
                false
            }
            else -> {
                markFailed(snapshot(), error)
                false
            }
        }
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
        // Off the main thread: an attachment can be large.
        val copied = withContext(Dispatchers.IO) {
            photoSources.mapIndexedNotNull { index, src -> copyIntoQueueDir(src, opId, index) }
        }
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
                serverId = activeServer().ifBlank { null },
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

    private companion object {
        const val ORPHAN_MIN_AGE_MS = 60 * 60 * 1000L
    }

    private fun toDomain(e: QueuedOpEntity): QueuedOp = QueuedOp(
        id = e.id,
        kind = runCatching { OpKind.valueOf(e.kind) }.getOrDefault(OpKind.UPDATE_ENTITY),
        tenant = e.tenant,
        serverId = e.serverId,
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
