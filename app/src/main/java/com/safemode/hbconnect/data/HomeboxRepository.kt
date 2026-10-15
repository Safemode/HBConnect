package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.remote.ApiProvider
import com.safemode.hbconnect.data.remote.HomeboxApi
import com.safemode.hbconnect.data.remote.models.ApiSummary
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntityListResult
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.data.remote.models.EntityUpdate
import com.safemode.hbconnect.data.remote.models.GroupStatistics
import com.safemode.hbconnect.data.remote.models.LoginForm
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import com.safemode.hbconnect.data.remote.models.MaintenanceEntryRequest
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagOut
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.data.remote.models.TagUpdate
import com.safemode.hbconnect.data.remote.models.TokenResponse
import com.safemode.hbconnect.data.remote.models.TreeItem
import com.safemode.hbconnect.data.remote.models.UserOut
import com.safemode.hbconnect.data.settings.ItemSortOrder
import kotlinx.coroutines.Dispatchers
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
        page: Int = 1,
        pageSize: Int = 200,
    ): Result<List<EntitySummary>> = call {
        it.getEntities(
            q = query?.ifBlank { null },
            page = page,
            pageSize = pageSize,
            isLocation = false,
        )
    }.map { it.items.orEmpty().sortedWith(sortComparator(sort)) }

    /** All locations (location entities), sorted by name. */
    suspend fun getLocations(): Result<List<EntitySummary>> = call {
        it.getEntities(pageSize = 500, isLocation = true)
    }.map { it.items.orEmpty().sortedBy { l -> l.displayName.lowercase() } }

    /** Direct children (items and sub-entities) of a given parent entity. */
    suspend fun getChildren(parentId: String): Result<List<EntitySummary>> = call {
        it.getEntities(parentIds = listOf(parentId), pageSize = 500)
    }.map { it.items.orEmpty().sortedWith(sortComparator(ItemSortOrder.NAME_ASC)) }

    suspend fun getEntity(id: String): Result<EntityOut> = call { it.getEntity(id) }

    suspend fun createEntity(body: EntityCreate): Result<EntityOut> = call { it.createEntity(body) }

    suspend fun updateEntity(id: String, body: EntityUpdate): Result<EntityOut> =
        call { it.updateEntity(id, body) }

    suspend fun deleteEntity(id: String): Result<Unit> = callUnit { it.deleteEntity(id) }

    suspend fun getTree(withItems: Boolean = false): Result<List<TreeItem>> =
        call { it.getTree(withItems) }

    suspend fun getEntityTypes(): Result<List<EntityTypeSummary>> = call { it.getEntityTypes() }

    /** The location entity types (isLocation == true). */
    suspend fun getLocationTypes(): Result<List<EntityTypeSummary>> =
        getEntityTypes().map { types -> types.filter { it.isLocation == true } }

    /** The item entity types (isLocation != true). */
    suspend fun getItemTypes(): Result<List<EntityTypeSummary>> =
        getEntityTypes().map { types -> types.filter { it.isLocation != true } }

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
