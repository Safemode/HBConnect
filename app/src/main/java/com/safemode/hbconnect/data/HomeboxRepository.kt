package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.remote.ApiProvider
import com.safemode.hbconnect.data.remote.HomeboxApi
import com.safemode.hbconnect.data.remote.models.ApiSummary
import com.safemode.hbconnect.data.remote.models.AttachmentUpdate
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.DuplicateOptions
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntityListResult
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntityPatch
import com.safemode.hbconnect.data.remote.models.EntityPath
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityTypeCreate
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.data.remote.models.EntityTypeUpdate
import com.safemode.hbconnect.data.remote.models.EntityUpdate
import com.safemode.hbconnect.data.remote.models.Group
import com.safemode.hbconnect.data.remote.models.GroupStatistics
import com.safemode.hbconnect.data.remote.models.LoginForm
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import com.safemode.hbconnect.data.remote.models.MaintenanceEntryRequest
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagOut
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.data.remote.models.TagUpdate
import com.safemode.hbconnect.data.remote.models.TokenResponse
import com.safemode.hbconnect.data.remote.models.TotalsByOrganizer
import com.safemode.hbconnect.data.remote.models.TreeItem
import com.safemode.hbconnect.data.remote.models.UserOut
import com.safemode.hbconnect.data.remote.models.ValueOverTime
import com.safemode.hbconnect.data.settings.ItemSortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/**
 * Single entry point the UI uses to talk to Homebox. Wraps API calls in [Result] and maps
 * transport errors onto readable messages.
 */
class HomeboxRepository(private val apiProvider: ApiProvider) {

    private fun api(): HomeboxApi = apiProvider.apiOrNull()
        ?: throw IllegalStateException("Connection not configured. Add your server details under Server.")

    private suspend fun <T> call(block: suspend (HomeboxApi) -> Response<T>): Result<T> =
        runCatching {
            val response = block(api())
            if (response.isSuccessful) {
                response.body() ?: throw IllegalStateException("Empty response from server.")
            } else {
                throw mapError(response.code(), response.errorBody()?.string())
            }
        }

    private suspend fun callUnit(block: suspend (HomeboxApi) -> Response<*>): Result<Unit> =
        runCatching {
            val response = block(api())
            if (response.isSuccessful) Unit
            else throw mapError(response.code(), response.errorBody()?.string())
        }

    private fun mapError(code: Int, body: String?): Exception {
        val detail = body?.take(200)?.ifBlank { null }
        return when (code) {
            401 -> IllegalStateException("Unauthorized (401). Your session may have expired — sign in again under Server.")
            403 -> IllegalStateException("Forbidden (403). This account lacks access.")
            404 -> IllegalStateException("Not found (404). Check the server address.")
            else -> IllegalStateException("Server error $code${detail?.let { ": $it" } ?: ""}")
        }
    }

    private fun <T> Response<T>.unwrap(): T {
        if (isSuccessful) return body() ?: throw IllegalStateException("Empty response from server.")
        throw mapError(code(), errorBody()?.string())
    }

    // ---- Auth / system ----
    suspend fun status(): Result<ApiSummary> = call { it.status() }

    suspend fun login(username: String, password: String): Result<TokenResponse> =
        call { it.login(LoginForm(username = username, password = password, stayLoggedIn = true)) }

    suspend fun refresh(): Result<TokenResponse> = call { it.refresh() }

    suspend fun logout(): Result<Unit> = callUnit { it.logout() }

    suspend fun self(): Result<UserOut> =
        call { it.self() }.mapCatching {
            it.item ?: throw IllegalStateException("No user returned.")
        }

    suspend fun statistics(): Result<GroupStatistics> = call { it.statistics() }

    suspend fun statsByLocation(): Result<List<TotalsByOrganizer>> = call { it.statsByLocation() }

    suspend fun statsByTag(): Result<List<TotalsByOrganizer>> = call { it.statsByTag() }

    suspend fun statsValueOverTime(start: String? = null, end: String? = null): Result<ValueOverTime> =
        call { it.statsValueOverTime(start, end) }

    suspend fun group(): Result<Group> = call { it.group() }

    suspend fun currencies(): Result<List<Currency>> = call { it.currencies() }

    suspend fun serverVersion(): Result<String> = status().map { s ->
        s.build?.version?.ifBlank { null }
            ?: s.versions?.firstOrNull()?.ifBlank { null }
            ?: "—"
    }

    // ---- Entities ----

    /** Items (non-location entities), optionally filtered by [query], sorted per [sort]. */
    suspend fun getItems(
        query: String? = null,
        sort: ItemSortOrder = ItemSortOrder.NAME_ASC,
        includeArchived: Boolean = false,
        page: Int = 1,
        pageSize: Int = 200,
    ): Result<List<EntitySummary>> = call {
        it.getEntities(
            q = query?.ifBlank { null },
            page = page,
            pageSize = pageSize,
            isLocation = false,
            includeArchived = includeArchived.takeIf { v -> v },
        )
    }.map { it.items.orEmpty().sortedWith(sortComparator(sort)) }

    /** All locations (location entities), sorted by name. */
    suspend fun getLocations(includeArchived: Boolean = false): Result<List<EntitySummary>> = call {
        it.getEntities(
            pageSize = 500,
            isLocation = true,
            includeArchived = includeArchived.takeIf { v -> v },
        )
    }.map { it.items.orEmpty().sortedBy { l -> l.displayName.lowercase() } }

    /**
     * Server-wide search across items and (when sensible) locations for the dashboard search. The
     * entities endpoint returns one kind per call, so items and locations are queried in parallel
     * and merged — items first, then locations, each by name.
     *
     * Locations are only queried for plain text/tag searches: when an item-centric filter is set
     * (a photo filter, a custom-field match, or a location filter) they're skipped, since those
     * facets don't meaningfully apply to locations.
     */
    suspend fun searchEntities(
        query: String,
        filters: EntityFilters = EntityFilters(),
        limit: Int = 50,
    ): Result<List<EntitySummary>> {
        val q = query.trim().ifBlank { null }
        if (q == null && !filters.isActive) return Result.success(emptyList())

        val tagIds = filters.tagIds.ifEmpty { null }
        val parentIds = filters.locationIds.ifEmpty { null }
        val fieldParams = filters.fields.map { it.toParam() }.ifEmpty { null }
        val archived = filters.includeArchived.takeIf { it }
        val negate = filters.negateTags.takeIf { it }
        val onlyWithPhoto = (filters.photo == PhotoFilter.WITH).takeIf { it }
        val onlyWithoutPhoto = (filters.photo == PhotoFilter.WITHOUT).takeIf { it }
        val includeLocations = filters.photo == PhotoFilter.ANY &&
            filters.fields.isEmpty() && filters.locationIds.isEmpty()
        val orderBy = filters.sort.orderBy
        val orderDirection = filters.sort.orderDirection

        return runCatching {
            coroutineScope {
                val items = async {
                    call {
                        it.getEntities(
                            q = q, pageSize = limit, isLocation = false,
                            tags = tagIds, parentIds = parentIds,
                            includeArchived = archived, negateTags = negate,
                            onlyWithPhoto = onlyWithPhoto, onlyWithoutPhoto = onlyWithoutPhoto,
                            fields = fieldParams, orderBy = orderBy, orderDirection = orderDirection,
                        )
                    }.getOrThrow().items.orEmpty()
                }
                val locations = async {
                    if (!includeLocations) emptyList()
                    else call {
                        it.getEntities(
                            q = q, pageSize = limit, isLocation = true,
                            tags = tagIds, includeArchived = archived, negateTags = negate,
                            orderBy = orderBy, orderDirection = orderDirection,
                        )
                    }.getOrThrow().items.orEmpty()
                }
                // Re-sort the merged list client-side so the two result sets interleave correctly.
                val comparator = when (filters.sort) {
                    SearchSort.NAME -> compareBy<EntitySummary> { it.displayName.lowercase() }
                    SearchSort.CREATED -> compareByDescending<EntitySummary> { it.createdAt.orEmpty() }
                    SearchSort.UPDATED -> compareByDescending<EntitySummary> { it.updatedAt.orEmpty() }
                }
                (items.await() + locations.await()).distinctBy { it.id }.sortedWith(comparator)
            }
        }
    }

    /** Distinct custom-field names across the group's entities (for the search field selector). */
    suspend fun getCustomFieldNames(): Result<List<String>> =
        call { it.getCustomFieldNames() }.map { names -> names.filter { it.isNotBlank() }.sorted() }

    /** Distinct values recorded for a given custom-field [name]. */
    suspend fun getCustomFieldValues(name: String): Result<List<String>> =
        call { it.getCustomFieldValues(name) }.map { values -> values.filter { it.isNotBlank() }.sorted() }

    /** Direct children (items and sub-entities) of a given parent entity. */
    suspend fun getChildren(
        parentId: String,
        includeArchived: Boolean = false,
    ): Result<List<EntitySummary>> = call {
        it.getEntities(
            parentIds = listOf(parentId),
            pageSize = 500,
            includeArchived = includeArchived.takeIf { v -> v },
        )
    }.map { it.items.orEmpty().sortedWith(sortComparator(ItemSortOrder.NAME_ASC)) }

    suspend fun getEntity(id: String): Result<EntityOut> = call { it.getEntity(id) }

    suspend fun createEntity(body: EntityCreate): Result<EntityOut> = call { it.createEntity(body) }

    suspend fun updateEntity(id: String, body: EntityUpdate): Result<EntityOut> =
        call { it.updateEntity(id, body) }

    /** Partial update — only the non-null fields of [body] are changed. */
    suspend fun patchEntity(id: String, body: EntityPatch): Result<EntityOut> =
        call { it.patchEntity(id, body) }

    suspend fun duplicateEntity(id: String, options: DuplicateOptions): Result<EntityOut> =
        call { it.duplicateEntity(id, options) }

    suspend fun deleteEntity(id: String): Result<Unit> = callUnit { it.deleteEntity(id) }

    /** Items matching an asset id (usually one); empty when nothing matches. */
    suspend fun findByAssetId(assetId: String): Result<List<EntitySummary>> =
        call { it.getByAssetId(assetId) }.map { it.items.orEmpty() }

    suspend fun getTree(withItems: Boolean = false): Result<List<TreeItem>> =
        call { it.getTree(withItems) }

    /** Ancestor path for an entity, root-first (the last element is the entity itself). */
    suspend fun getEntityPath(id: String): Result<List<EntityPath>> =
        call { it.getEntityPath(id) }

    suspend fun getEntityTypes(): Result<List<EntityTypeSummary>> = call { it.getEntityTypes() }

    /** The location entity types (isLocation == true). */
    suspend fun getLocationTypes(): Result<List<EntityTypeSummary>> =
        getEntityTypes().map { types -> types.filter { it.isLocation == true } }

    /** The item entity types (isLocation != true). */
    suspend fun getItemTypes(): Result<List<EntityTypeSummary>> =
        getEntityTypes().map { types -> types.filter { it.isLocation != true } }

    suspend fun createEntityType(body: EntityTypeCreate): Result<EntityTypeSummary> =
        call { it.createEntityType(body) }

    suspend fun updateEntityType(id: String, body: EntityTypeUpdate): Result<EntityTypeSummary> =
        call { it.updateEntityType(id, body) }

    suspend fun deleteEntityType(id: String): Result<Unit> = callUnit { it.deleteEntityType(id) }

    // ---- Tags ----
    suspend fun getTags(): Result<List<TagSummary>> =
        call { it.getTags() }.map { it.sortedBy { t -> t.name?.lowercase().orEmpty() } }

    suspend fun createTag(body: TagCreate): Result<TagOut> = call { it.createTag(body) }

    suspend fun updateTag(id: String, body: TagUpdate): Result<TagOut> =
        call { it.updateTag(id, body) }

    suspend fun deleteTag(id: String): Result<Unit> = callUnit { it.deleteTag(id) }

    // ---- Attachments / images ----

    /** Absolute URL for an entity attachment, used by Coil with the authenticated client. */
    fun attachmentUrl(entityId: String, attachmentId: String): String {
        val base = apiProvider.currentConfig().baseUrl().trimEnd('/')
        return "$base/api/v1/entities/$entityId/attachments/$attachmentId"
    }

    /** Downloads an attachment's bytes using the authenticated client (off the main thread). */
    suspend fun downloadAttachment(entityId: String, attachmentId: String): Result<ByteArray> =
        runCatching {
            val url = attachmentUrl(entityId, attachmentId)
            val client = apiProvider.authenticatedImageClient()
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).build()).execute().use {
                    if (!it.isSuccessful) throw IllegalStateException("Download failed (${it.code}).")
                    it.body.bytes()
                }
            }
        }

    suspend fun deleteAttachment(entityId: String, attachmentId: String): Result<Unit> =
        callUnit { it.deleteAttachment(entityId, attachmentId) }

    // ---- Label maker ----

    /** URL of the server-generated PNG label (QR + title) for an item or location. */
    fun labelUrl(entityId: String, isLocation: Boolean): String {
        val base = apiProvider.currentConfig().baseUrl().trimEnd('/')
        val kind = if (isLocation) "location" else "item"
        return "$base/api/v1/labelmaker/$kind/$entityId"
    }

    /** Downloads the server-rendered label PNG using the authenticated client (off the main thread). */
    suspend fun downloadLabel(entityId: String, isLocation: Boolean): Result<ByteArray> =
        runCatching {
            val url = labelUrl(entityId, isLocation)
            val client = apiProvider.authenticatedImageClient()
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).build()).execute().use {
                    if (!it.isSuccessful) throw IllegalStateException("Label request failed (${it.code}).")
                    it.body.bytes()
                }
            }
        }

    /** Updates an attachment's title, type, and primary flag (all required by the API). */
    suspend fun updateAttachment(
        entityId: String,
        attachmentId: String,
        type: String,
        title: String,
        primary: Boolean,
    ): Result<Unit> = call {
        it.updateAttachment(entityId, attachmentId, AttachmentUpdate(type = type, title = title, primary = primary))
    }.map { }

    /** Uploads a file as an attachment on an entity. [type] is e.g. "photo" or "attachment". */
    suspend fun uploadAttachment(
        entityId: String,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        type: String = "attachment",
        primary: Boolean = false,
    ): Result<Unit> = callUnit { api ->
        val media = (mimeType ?: "application/octet-stream").toMediaTypeOrNull()
        val parts = listOf(
            MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody(media)),
            MultipartBody.Part.createFormData("type", type),
            MultipartBody.Part.createFormData("name", fileName),
            MultipartBody.Part.createFormData("primary", primary.toString()),
        )
        api.uploadAttachment(entityId, parts)
    }

    // ---- Maintenance ----
    suspend fun getMaintenance(entityId: String): Result<List<MaintenanceEntry>> =
        call { it.getMaintenance(entityId) }

    /** Group-wide maintenance entries. [status] is scheduled, completed, or both. */
    suspend fun getAllMaintenance(status: String): Result<List<MaintenanceEntry>> =
        call { it.getAllMaintenance(status) }

    suspend fun createMaintenance(entityId: String, body: MaintenanceEntryRequest): Result<Unit> =
        callUnit { it.createMaintenance(entityId, body) }

    suspend fun updateMaintenance(id: String, body: MaintenanceEntryRequest): Result<Unit> =
        callUnit { it.updateMaintenance(id, body) }

    suspend fun deleteMaintenance(id: String): Result<Unit> =
        callUnit { it.deleteMaintenance(id) }

    /** Clears the saved session so the user returns to a disconnected state. */
    suspend fun clearSession() {
        val cfg = apiProvider.currentConfig()
        // Best-effort server-side logout for token sessions; ignore failures.
        runCatching { if (cfg.token.isNotBlank()) logout() }
    }

    private fun sortComparator(sort: ItemSortOrder): Comparator<EntitySummary> = when (sort) {
        ItemSortOrder.NAME_ASC -> compareBy { it.displayName.lowercase() }
        ItemSortOrder.NAME_DESC -> compareByDescending { it.displayName.lowercase() }
        ItemSortOrder.NEWEST_FIRST -> compareByDescending { it.createdAt.orEmpty() }
        ItemSortOrder.OLDEST_FIRST -> compareBy { it.createdAt.orEmpty() }
    }
}
