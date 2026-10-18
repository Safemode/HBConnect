package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.local.CachedEntity
import com.safemode.hbconnect.data.local.CachedEntityDao
import com.safemode.hbconnect.data.remote.ApiProvider
import com.safemode.hbconnect.data.remote.HomeboxApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.safemode.hbconnect.data.remote.models.ApiKeyCreate
import com.safemode.hbconnect.data.remote.models.ApiKeyCreatedOut
import com.safemode.hbconnect.data.remote.models.ApiKeyOut
import com.safemode.hbconnect.data.remote.models.ApiSummary
import com.safemode.hbconnect.data.remote.models.AttachmentUpdate
import com.safemode.hbconnect.data.remote.models.ChangePassword
import com.safemode.hbconnect.data.remote.models.UserUpdate
import com.safemode.hbconnect.data.remote.models.BarcodeProduct
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.DuplicateOptions
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntityListResult
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntityPatch
import com.safemode.hbconnect.data.remote.models.EntityPath
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityTemplateCreate
import com.safemode.hbconnect.data.remote.models.EntityTemplateCreateItem
import com.safemode.hbconnect.data.remote.models.EntityTemplateOut
import com.safemode.hbconnect.data.remote.models.EntityTemplateSummary
import com.safemode.hbconnect.data.remote.models.EntityTemplateUpdate
import com.safemode.hbconnect.data.remote.models.EntityTypeCreate
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.data.remote.models.EntityTypeUpdate
import com.safemode.hbconnect.data.remote.models.EntityUpdate
import com.safemode.hbconnect.data.remote.models.ExportOut
import com.safemode.hbconnect.data.remote.models.Group
import com.safemode.hbconnect.data.remote.models.GroupCreate
import com.safemode.hbconnect.data.remote.models.GroupUpdate
import com.safemode.hbconnect.data.remote.models.GroupAcceptInvitation
import com.safemode.hbconnect.data.remote.models.GroupInvitation
import com.safemode.hbconnect.data.remote.models.GroupInvitationCreate
import com.safemode.hbconnect.data.remote.models.GroupInvitationCreated
import com.safemode.hbconnect.data.remote.models.GroupMember
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
import com.safemode.hbconnect.data.remote.models.ValueInsights
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
import java.io.OutputStream

/**
 * Single entry point the UI uses to talk to Homebox. Wraps API calls in [Result] and maps
 * transport errors onto readable messages.
 */
class HomeboxRepository(
    private val apiProvider: ApiProvider,
    private val entityCache: CachedEntityDao? = null,
) {

    private fun api(): HomeboxApi = apiProvider.apiOrNull()
        ?: throw IllegalStateException("Connection not configured. Add your server details under Server.")

    // Used to (de)serialize entity summaries into the local cache for offline reads/search.
    private val cacheMoshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val summaryAdapter = cacheMoshi.adapter(EntitySummary::class.java)

    /** The collection the cache rows belong to (blank = the server's default group). */
    private fun cacheTenant(): String = apiProvider.currentConfig().activeGroupId

    /** Upserts fetched entities into the local cache so the lists and search keep working offline. */
    private suspend fun cacheEntities(entities: List<EntitySummary>) {
        val dao = entityCache ?: return
        val tenant = cacheTenant()
        val rows = entities.mapNotNull { s ->
            val id = s.id ?: return@mapNotNull null
            CachedEntity(
                id = id,
                tenant = tenant,
                isLocation = s.isLocation,
                name = s.displayName,
                archived = s.archived == true,
                updatedAt = s.updatedAt.orEmpty(),
                summaryJson = summaryAdapter.toJson(s),
            )
        }
        if (rows.isNotEmpty()) runCatching { dao.upsertAll(rows) }
    }

    private suspend fun cachedList(isLocation: Boolean, includeArchived: Boolean): List<EntitySummary> {
        val dao = entityCache ?: return emptyList()
        return dao.list(cacheTenant(), isLocation, includeArchived)
            .mapNotNull { runCatching { summaryAdapter.fromJson(it) }.getOrNull() }
    }

    private suspend fun cachedSearch(query: String, includeArchived: Boolean, limit: Int): List<EntitySummary> {
        val dao = entityCache ?: return emptyList()
        return dao.search(cacheTenant(), query, includeArchived, limit)
            .mapNotNull { runCatching { summaryAdapter.fromJson(it) }.getOrNull() }
    }

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
            401 -> IllegalStateException("Unauthorized (401). Your session may have expired - sign in again under Server.")
            403 -> IllegalStateException("Forbidden (403). This account lacks access.")
            404 -> IllegalStateException("Not found (404). Check the server address.")
            else -> IllegalStateException("Server error $code${detail?.let { ": $it" } ?: ""}")
        }
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

    suspend fun logoutAll(): Result<Unit> = callUnit { it.logoutAll() }

    /** Updates the signed-in user's name and email. */
    suspend fun updateSelf(name: String, email: String): Result<Unit> =
        callUnit { it.updateSelf(UserUpdate(name = name, email = email)) }

    suspend fun changePassword(current: String, new: String): Result<Unit> =
        callUnit { it.changePassword(ChangePassword(current = current, new = new)) }

    // ---- Personal API keys ----
    suspend fun getApiKeys(): Result<List<ApiKeyOut>> = call { it.getApiKeys() }

    /** Creates an API key; [expiresAt] is an ISO-8601 instant, or null for a key that never expires. */
    suspend fun createApiKey(name: String, expiresAt: String?): Result<ApiKeyCreatedOut> =
        call { it.createApiKey(ApiKeyCreate(name = name, expiresAt = expiresAt)) }

    suspend fun deleteApiKey(id: String): Result<Unit> = callUnit { it.deleteApiKey(id) }

    suspend fun statistics(): Result<GroupStatistics> = call { it.statistics() }

    suspend fun statsValueOverTime(start: String? = null, end: String? = null): Result<ValueOverTime> =
        call { it.statsValueOverTime(start, end) }

    /**
     * Value analysis for the Value breakdown screen, computed from the group's non-archived items.
     * The server's /statistics/locations and /statistics/tags endpoints sum archived items too,
     * which contradicts the archived-excluded totals shown elsewhere, so this is computed in-app.
     * Grouping by manufacturer relies on that field being present on the entity list rows; items
     * without a manufacturer simply don't contribute to that breakdown.
     */
    suspend fun valueInsights(): Result<ValueInsights> = runCatching {
        val items = allItems(includeArchived = false).getOrThrow()
        val locTotal = LinkedHashMap<String, Double>()
        val locName = HashMap<String, String>()
        val tagTotal = LinkedHashMap<String, Double>()
        val tagName = HashMap<String, String>()
        val mfgTotal = LinkedHashMap<String, Double>()
        val typeTotal = LinkedHashMap<String, Double>()
        val typeName = HashMap<String, String>()
        var total = 0.0
        var priced = 0
        var insured = 0.0
        var uninsured = 0.0
        items.forEach { item ->
            val price = item.purchasePrice ?: 0.0
            total += price
            if (price > 0.0) priced++
            if (item.insured == true) insured += price else uninsured += price
            item.parent?.id?.let { id ->
                locName[id] = item.parent.displayName
                locTotal[id] = (locTotal[id] ?: 0.0) + price
            }
            item.tags.orEmpty().forEach { tag ->
                tag.id?.let { id ->
                    tagName[id] = tag.name ?: "tag"
                    tagTotal[id] = (tagTotal[id] ?: 0.0) + price
                }
            }
            item.manufacturer?.trim()?.ifBlank { null }?.let { m ->
                mfgTotal[m] = (mfgTotal[m] ?: 0.0) + price
            }
            item.entityType?.let { type ->
                val key = type.id ?: type.name
                if (key != null) {
                    typeName[key] = type.name ?: "Type"
                    typeTotal[key] = (typeTotal[key] ?: 0.0) + price
                }
            }
        }
        ValueInsights(
            totalValue = total,
            itemCount = items.size,
            pricedCount = priced,
            insuredValue = insured,
            uninsuredValue = uninsured,
            byLocation = locTotal.map { (id, t) -> TotalsByOrganizer(id, locName[id], t) },
            byTag = tagTotal.map { (id, t) -> TotalsByOrganizer(id, tagName[id], t) },
            byManufacturer = mfgTotal.map { (name, t) -> TotalsByOrganizer(name, name, t) },
            byType = typeTotal.map { (id, t) -> TotalsByOrganizer(id, typeName[id], t) },
            mostExpensive = items
                .filter { (it.purchasePrice ?: 0.0) > 0.0 }
                .sortedByDescending { it.purchasePrice ?: 0.0 }
                .take(5),
        )
    }

    /** Fetches every item (optionally including archived), paging until the full set is retrieved. */
    private suspend fun allItems(includeArchived: Boolean): Result<List<EntitySummary>> = runCatching {
        val acc = ArrayList<EntitySummary>()
        val pageSize = 200
        var page = 1
        while (page <= 100) {
            val result = call {
                it.getEntities(
                    page = page,
                    pageSize = pageSize,
                    isLocation = false,
                    includeArchived = includeArchived.takeIf { v -> v },
                )
            }.getOrThrow()
            val batch = result.items.orEmpty()
            acc += batch
            val total = result.total ?: acc.size
            if (batch.size < pageSize || acc.size >= total) break
            page++
        }
        acc
    }

    /** A group - the active collection, or the one named by [tenant]. */
    suspend fun group(tenant: String? = null): Result<Group> = call { it.group(tenant) }

    /** Every collection (group) the user belongs to. */
    suspend fun groupsAll(): Result<List<Group>> = call { it.groupsAll() }

    /** Creates a new collection with [name] and returns it. */
    suspend fun createGroup(name: String): Result<Group> =
        call { it.createGroup(GroupCreate(name = name)) }

    /**
     * Updates a group's name and currency (owner only). [tenant] targets a specific collection
     * instead of the active one, so a collection other than the current one can be renamed.
     */
    suspend fun updateGroup(name: String, currency: String, tenant: String? = null): Result<Group> =
        call { it.updateGroup(GroupUpdate(name = name, currency = currency), tenant) }

    // ---- Group members / invitations ----
    suspend fun groupMembers(): Result<List<GroupMember>> = call { it.groupMembers() }

    suspend fun removeGroupMember(userId: String): Result<Unit> =
        callUnit { it.removeGroupMember(userId) }

    suspend fun groupInvitations(): Result<List<GroupInvitation>> = call { it.groupInvitations() }

    /** [expiresAt] is an ISO-8601 instant; null lets the server default it to 24 hours. */
    suspend fun createGroupInvitation(uses: Int, expiresAt: String? = null): Result<GroupInvitationCreated> =
        call { it.createGroupInvitation(GroupInvitationCreate(uses = uses, expiresAt = expiresAt)) }

    suspend fun deleteGroupInvitation(id: String): Result<Unit> =
        callUnit { it.deleteGroupInvitation(id) }

    /** Accepts an invitation [token], joining that collection. */
    suspend fun acceptGroupInvitation(token: String): Result<GroupAcceptInvitation> =
        call { it.acceptGroupInvitation(token) }

    suspend fun currencies(): Result<List<Currency>> = call { it.currencies() }

    suspend fun serverVersion(): Result<String> = status().map { s ->
        s.build?.version?.ifBlank { null }
            ?: s.versions?.firstOrNull()?.ifBlank { null }
            ?: "-"
    }

    // ---- Entities ----

    /** Items (non-location entities), optionally filtered by [query], sorted per [sort]. */
    suspend fun getItems(
        query: String? = null,
        sort: ItemSortOrder = ItemSortOrder.NAME_ASC,
        includeArchived: Boolean = false,
        page: Int = 1,
        pageSize: Int = 200,
    ): Result<List<EntitySummary>> {
        val result = call {
            it.getEntities(
                q = query?.ifBlank { null },
                page = page,
                pageSize = pageSize,
                isLocation = false,
                includeArchived = includeArchived.takeIf { v -> v },
            )
        }.map { it.items.orEmpty().sortedWith(sortComparator(sort)) }
        return result.fold(
            onSuccess = { items -> cacheEntities(items); Result.success(items) },
            onFailure = { e ->
                // Offline: serve the last-synced copy from the local cache (search when a query is set).
                val cached = if (!query.isNullOrBlank()) {
                    cachedSearch(query, includeArchived, pageSize).filterNot { it.isLocation }
                } else {
                    cachedList(isLocation = false, includeArchived = includeArchived)
                }
                if (cached.isNotEmpty()) Result.success(cached.sortedWith(sortComparator(sort))) else Result.failure(e)
            },
        )
    }

    /** The most recently created items, newest first (for the dashboard's "Recently added"). */
    suspend fun getRecentItems(limit: Int = 3): Result<List<EntitySummary>> = call {
        it.getEntities(
            pageSize = limit,
            isLocation = false,
            orderBy = SearchSort.CREATED.orderBy,
            orderDirection = SearchSort.CREATED.orderDirection,
        )
    }.map { it.items.orEmpty() }

    /** All locations (location entities), sorted by name. */
    suspend fun getLocations(includeArchived: Boolean = false): Result<List<EntitySummary>> {
        val result = call {
            it.getEntities(
                pageSize = 500,
                isLocation = true,
                includeArchived = includeArchived.takeIf { v -> v },
            )
        }.map { it.items.orEmpty().sortedBy { l -> l.displayName.lowercase() } }
        return result.fold(
            onSuccess = { locations -> cacheEntities(locations); Result.success(locations) },
            onFailure = { e ->
                val cached = cachedList(isLocation = true, includeArchived = includeArchived)
                if (cached.isNotEmpty()) Result.success(cached.sortedBy { it.displayName.lowercase() }) else Result.failure(e)
            },
        )
    }

    /**
     * Server-wide search across items and (when sensible) locations for the dashboard search. The
     * entities endpoint returns one kind per call, so items and locations are queried in parallel
     * and merged - items first, then locations, each by name.
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

        val result = runCatching {
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
        return result.fold(
            onSuccess = { merged -> cacheEntities(merged); Result.success(merged) },
            onFailure = { e ->
                // Offline: fall back to a local name search over the cached entities.
                val cached = if (q != null) cachedSearch(q, filters.includeArchived, limit) else emptyList()
                if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
            },
        )
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
    ): Result<List<EntitySummary>> {
        val result = call {
            it.getEntities(
                parentIds = listOf(parentId),
                pageSize = 500,
                includeArchived = includeArchived.takeIf { v -> v },
            )
        }.map { it.items.orEmpty().sortedWith(sortComparator(ItemSortOrder.NAME_ASC)) }
        // Feed children into the cache too, so offline search covers nested items.
        result.getOrNull()?.let { cacheEntities(it) }
        return result
    }

    suspend fun getEntity(id: String): Result<EntityOut> = call { it.getEntity(id) }

    suspend fun createEntity(body: EntityCreate): Result<EntityOut> = call { it.createEntity(body) }

    suspend fun updateEntity(id: String, body: EntityUpdate): Result<EntityOut> =
        call { it.updateEntity(id, body) }

    /** Partial update - only the non-null fields of [body] are changed. */
    suspend fun patchEntity(id: String, body: EntityPatch): Result<EntityOut> =
        call { it.patchEntity(id, body) }

    suspend fun duplicateEntity(id: String, options: DuplicateOptions): Result<EntityOut> =
        call { it.duplicateEntity(id, options) }

    suspend fun deleteEntity(id: String): Result<Unit> = callUnit { it.deleteEntity(id) }

    /** Items matching an asset id (usually one); empty when nothing matches. */
    suspend fun findByAssetId(assetId: String): Result<List<EntitySummary>> =
        call { it.getByAssetId(assetId) }.map { it.items.orEmpty() }

    /** Product lookup for a scanned UPC/EAN barcode; may return several candidates (or none). */
    suspend fun searchFromBarcode(ean: String): Result<List<BarcodeProduct>> =
        call { it.searchFromBarcode(ean.trim()) }

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

    // ---- Entity templates ----
    suspend fun getTemplates(): Result<List<EntityTemplateSummary>> =
        call { it.getTemplates() }.map { list -> list.sortedBy { t -> t.name?.lowercase().orEmpty() } }

    suspend fun getTemplate(id: String): Result<EntityTemplateOut> = call { it.getTemplate(id) }

    suspend fun createTemplate(body: EntityTemplateCreate): Result<EntityTemplateOut> =
        call { it.createTemplate(body) }

    suspend fun updateTemplate(id: String, body: EntityTemplateUpdate): Result<EntityTemplateOut> =
        call { it.updateTemplate(id, body) }

    suspend fun deleteTemplate(id: String): Result<Unit> = callUnit { it.deleteTemplate(id) }

    suspend fun createItemFromTemplate(id: String, body: EntityTemplateCreateItem): Result<EntityOut> =
        call { it.createItemFromTemplate(id, body) }

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

    // ---- Deep links ----

    /**
     * The Homebox web URL for an entity, e.g. `https://host/item/{id}`, built from the
     * connected server address. Used to encode a scannable QR code that opens the entity.
     */
    fun entityWebUrl(entityId: String, isLocation: Boolean): String {
        val base = apiProvider.currentConfig().baseUrl().trimEnd('/')
        val kind = if (isLocation) "location" else "item"
        return "$base/$kind/$entityId"
    }

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

    // ---- CSV import / export ----
    /** Downloads the group's entities as CSV bytes (text/csv). */
    suspend fun exportCsv(): Result<ByteArray> = runCatching {
        val response = api().exportEntities()
        if (response.isSuccessful) {
            (response.body() ?: throw IllegalStateException("Empty response from server."))
                .use { it.bytes() }
        } else {
            throw mapError(response.code(), response.errorBody()?.string())
        }
    }

    /** Uploads a Homebox CSV to create/update entities. */
    suspend fun importCsv(fileName: String, bytes: ByteArray): Result<Unit> = callUnit { api ->
        val part = MultipartBody.Part.createFormData(
            "csv", fileName, bytes.toRequestBody("text/csv".toMediaTypeOrNull()),
        )
        api.importEntities(part)
    }

    /** Downloads the bill of materials for the active collection as CSV bytes (text/csv). */
    suspend fun billOfMaterialsCsv(): Result<ByteArray> = runCatching {
        val response = api().billOfMaterials()
        if (response.isSuccessful) {
            (response.body() ?: throw IllegalStateException("Empty response from server."))
                .use { it.bytes() }
        } else {
            throw mapError(response.code(), response.errorBody()?.string())
        }
    }

    // ---- Whole-collection backup (async server-side exports) ----
    /** Every backup job for the active collection, newest first. */
    suspend fun listExports(): Result<List<ExportOut>> =
        call { it.listExports() }.map { result ->
            result.items.orEmpty().sortedByDescending { it.createdAt.orEmpty() }
        }

    /** Enqueues a new backup; the archive is built on the server, nothing is downloaded here. */
    suspend fun createExport(): Result<ExportOut> = call { it.createExport() }

    suspend fun getExport(id: String): Result<ExportOut> = call { it.getExport(id) }

    suspend fun deleteExport(id: String): Result<Unit> = callUnit { it.deleteExport(id) }

    /** Streams a completed backup's zip bytes to [sink] (a stream the caller opens and closes). */
    suspend fun downloadExportTo(id: String, sink: OutputStream): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val response = api().downloadExport(id)
            if (!response.isSuccessful) throw mapError(response.code(), response.errorBody()?.string())
            val body = response.body() ?: throw IllegalStateException("Empty response from server.")
            body.byteStream().use { input -> input.copyTo(sink) }
        }
    }

    /** Restores a whole collection from a previously exported backup zip. */
    suspend fun importCollection(fileName: String, bytes: ByteArray): Result<ExportOut> =
        call { api ->
            val part = MultipartBody.Part.createFormData(
                "file", fileName, bytes.toRequestBody("application/zip".toMediaTypeOrNull()),
            )
            api.importCollection(part)
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
