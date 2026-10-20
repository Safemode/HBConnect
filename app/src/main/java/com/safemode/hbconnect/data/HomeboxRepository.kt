package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.local.CachedDetail
import com.safemode.hbconnect.data.local.CachedDetailDao
import com.safemode.hbconnect.data.local.CachedEntity
import com.safemode.hbconnect.data.local.CachedEntityDao
import com.safemode.hbconnect.data.remote.ApiProvider
import com.safemode.hbconnect.data.remote.HomeboxApi
import com.safemode.hbconnect.data.remote.ServerUnreachableException
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
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import java.io.OutputStream

/**
 * Single entry point the UI uses to talk to Homebox. Wraps API calls in [Result] and maps
 * transport errors onto readable messages.
 */
class HomeboxRepository(
    private val apiProvider: ApiProvider,
    private val entityCache: CachedEntityDao? = null,
    private val detailCache: CachedDetailDao? = null,
) {

    private fun api(): HomeboxApi = apiProvider.apiOrNull()
        ?: throw IllegalStateException("Connection not configured. Add your server details under Server.")

    // Used to (de)serialize entity summaries into the local cache for offline reads/search.
    private val cacheMoshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val summaryAdapter = cacheMoshi.adapter(EntitySummary::class.java)
    private val detailAdapter = cacheMoshi.adapter(EntityOut::class.java)

    /** The saved server profile the cache rows belong to. */
    private fun cacheServer(): String = apiProvider.currentConfig().serverId

    /** The collection the cache rows belong to (blank = the server's default group). */
    private fun cacheTenant(): String = apiProvider.currentConfig().activeGroupId

    /**
     * Upserts fetched entities into the local cache so the lists and search keep working offline.
     * [server] and [tenant] default to the active ones; a caller that fetched over a longer span
     * (the background sync) passes the scope captured when it started, so a server or collection
     * switch mid-fetch can't file rows under the wrong one.
     */
    private suspend fun cacheEntities(
        entities: List<EntitySummary>,
        server: String = cacheServer(),
        tenant: String = cacheTenant(),
    ) {
        val dao = entityCache ?: return
        val rows = entities.mapNotNull { s ->
            val id = s.id ?: return@mapNotNull null
            CachedEntity(
                id = id,
                serverId = server,
                tenant = tenant,
                isLocation = s.isLocation,
                name = s.displayName,
                archived = s.archived == true,
                parentId = s.parent?.id,
                updatedAt = s.updatedAt.orEmpty(),
                summaryJson = summaryAdapter.toJson(s),
            )
        }
        if (rows.isNotEmpty()) runCatchingCancellable { dao.upsertAll(rows) }
    }

    /** Stores an entity's full detail for offline detail screens. */
    private suspend fun cacheDetail(entity: EntityOut) {
        val dao = detailCache ?: return
        val id = entity.id ?: return
        runCatchingCancellable { dao.upsert(CachedDetail(id, entity.updatedAt.orEmpty(), detailAdapter.toJson(entity))) }
    }

    /**
     * Removes cached rows (summary and detail) for [server]/[tenant] whose id isn't in [keep]: the
     * full current set, so anything missing was deleted on the server. Deleted in chunks to stay under
     * SQLite's bound-parameter limit.
     */
    private suspend fun pruneDeleted(
        entities: CachedEntityDao,
        details: CachedDetailDao,
        server: String,
        tenant: String,
        keep: Set<String>,
    ) {
        runCatchingCancellable {
            val stale = entities.idsIn(server, tenant).filterNot { it in keep }
            stale.chunked(500).forEach { chunk ->
                entities.deleteIds(chunk)
                details.deleteIds(chunk)
            }
        }
    }

    /** Drops one entity from the offline cache (after it was deleted). */
    private suspend fun uncache(id: String) {
        runCatchingCancellable { entityCache?.deleteIds(listOf(id)) }
        runCatchingCancellable { detailCache?.deleteIds(listOf(id)) }
    }

    private suspend fun cachedDetail(id: String): EntityOut? {
        val dao = detailCache ?: return null
        val json = dao.detail(id) ?: return null
        return runCatchingCancellable { detailAdapter.fromJson(json) }.getOrNull()
    }

    private suspend fun cachedChildren(parentId: String, includeArchived: Boolean): List<EntitySummary> {
        val dao = entityCache ?: return emptyList()
        return dao.childrenOf(cacheServer(), cacheTenant(), parentId, includeArchived)
            .mapNotNull { runCatchingCancellable { summaryAdapter.fromJson(it) }.getOrNull() }
    }

    private suspend fun cachedList(isLocation: Boolean, includeArchived: Boolean): List<EntitySummary> {
        val dao = entityCache ?: return emptyList()
        return dao.list(cacheServer(), cacheTenant(), isLocation, includeArchived)
            .mapNotNull { runCatchingCancellable { summaryAdapter.fromJson(it) }.getOrNull() }
    }

    private suspend fun cachedSearch(query: String, includeArchived: Boolean, limit: Int): List<EntitySummary> {
        val dao = entityCache ?: return emptyList()
        return dao.search(cacheServer(), cacheTenant(), query, includeArchived, limit)
            .mapNotNull { runCatchingCancellable { summaryAdapter.fromJson(it) }.getOrNull() }
    }

    private suspend fun <T> call(block: suspend (HomeboxApi) -> Response<T>): Result<T> =
        runCatchingCancellable {
            val response = block(api())
            if (response.isSuccessful) {
                response.body() ?: throw IllegalStateException("Empty response from server.")
            } else {
                throw errorFor(response)
            }
        }

    private suspend fun callUnit(block: suspend (HomeboxApi) -> Response<*>): Result<Unit> =
        runCatchingCancellable {
            val response = block(api())
            if (response.isSuccessful) Unit
            else throw errorFor(response)
        }

    /**
     * The exception for a failed [response]. A 504 that never touched the network is OkHttp's
     * synthetic answer to a cache-only lookup that missed (the server is unreachable, see
     * OfflineInterceptor), so it becomes a [ServerUnreachableException] rather than a server error.
     */
    private fun errorFor(response: Response<*>): Exception {
        if (response.code() == 504 && response.raw().networkResponse == null) return ServerUnreachableException()
        return mapError(response.code(), response.errorBody()?.string())
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
    suspend fun valueInsights(): Result<ValueInsights> = runCatchingCancellable {
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
    private suspend fun allItems(includeArchived: Boolean): Result<List<EntitySummary>> =
        allPages { api, page, size ->
            api.getEntities(
                page = page,
                pageSize = size,
                isLocation = false,
                includeArchived = includeArchived.takeIf { v -> v },
            )
        }

    /**
     * Fetches every page of an entities listing ([fetch] gets the api, the 1-based page and the page
     * size), stopping at the server's reported total or a short page. Fails if any page fails, so a
     * partial list is never mistaken for the whole set. [maxPages] caps the walk as a safety net.
     */
    private suspend fun allPages(
        pageSize: Int = PAGE_SIZE,
        maxPages: Int = MAX_PAGES,
        fetch: suspend (HomeboxApi, Int, Int) -> Response<EntityListResult>,
    ): Result<List<EntitySummary>> = runCatchingCancellable {
        val acc = ArrayList<EntitySummary>()
        var page = 1
        while (page <= maxPages) {
            val result = call { fetch(it, page, pageSize) }.getOrThrow()
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

    /**
     * Every item (non-location entity), optionally filtered by [query], sorted per [sort]. All pages
     * are fetched, with the server sorting too, so a large collection is complete and "newest first"
     * really is the newest across the whole collection rather than within one page.
     */
    suspend fun getItems(
        query: String? = null,
        sort: ItemSortOrder = ItemSortOrder.NAME_ASC,
        includeArchived: Boolean = false,
    ): Result<List<EntitySummary>> {
        val (orderBy, orderDirection) = when (sort) {
            ItemSortOrder.NAME_ASC -> SearchSort.NAME.orderBy to "asc"
            ItemSortOrder.NAME_DESC -> SearchSort.NAME.orderBy to "desc"
            ItemSortOrder.NEWEST_FIRST -> SearchSort.CREATED.orderBy to "desc"
            ItemSortOrder.OLDEST_FIRST -> SearchSort.CREATED.orderBy to "asc"
        }
        val result = allPages { api, page, size ->
            api.getEntities(
                q = query?.ifBlank { null },
                page = page,
                pageSize = size,
                isLocation = false,
                includeArchived = includeArchived.takeIf { v -> v },
                orderBy = orderBy,
                orderDirection = orderDirection,
            )
        }.map { it.sortedWith(sortComparator(sort)) }
        return result.fold(
            onSuccess = { items -> cacheEntities(items); Result.success(items) },
            onFailure = { e ->
                // Offline: serve the last-synced copy from the local cache (search when a query is set).
                val cached = if (!query.isNullOrBlank()) {
                    cachedSearch(query, includeArchived, OFFLINE_SEARCH_LIMIT).filterNot { it.isLocation }
                } else {
                    cachedList(isLocation = false, includeArchived = includeArchived)
                }
                if (cached.isNotEmpty()) Result.success(cached.sortedWith(sortComparator(sort))) else Result.failure(e)
            },
        )
    }

    /**
     * One page of items sorted by name, for pickers that load more as the user scrolls instead of
     * fetching the whole collection. [query] narrows by name on the server. Offline, the first page
     * comes from the cache (up to [OFFLINE_SEARCH_LIMIT] matches) and there are no more pages.
     */
    suspend fun getItemsPage(query: String?, page: Int, pageSize: Int = PICKER_PAGE_SIZE): Result<ItemPage> {
        val result = call {
            it.getEntities(
                q = query?.ifBlank { null },
                page = page,
                pageSize = pageSize,
                isLocation = false,
                orderBy = SearchSort.NAME.orderBy,
                orderDirection = "asc",
            )
        }
        return result.fold(
            onSuccess = { r ->
                val items = r.items.orEmpty()
                cacheEntities(items)
                val total = r.total
                Result.success(ItemPage(items, hasMore = items.size >= pageSize && (total == null || page * pageSize < total)))
            },
            onFailure = { e ->
                val cached = if (page == 1) {
                    cachedSearch(query.orEmpty(), includeArchived = false, limit = OFFLINE_SEARCH_LIMIT).filterNot { it.isLocation }
                } else {
                    emptyList()
                }
                if (cached.isNotEmpty()) Result.success(ItemPage(cached, hasMore = false)) else Result.failure(e)
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

    /** All locations (location entities, every page), sorted by name. */
    suspend fun getLocations(includeArchived: Boolean = false): Result<List<EntitySummary>> {
        val result = allPages { api, page, size ->
            api.getEntities(
                page = page,
                pageSize = size,
                isLocation = true,
                includeArchived = includeArchived.takeIf { v -> v },
            )
        }.map { it.sortedBy { l -> l.displayName.lowercase() } }
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

        val result = runCatchingCancellable {
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
        val result = allPages { api, page, size ->
            api.getEntities(
                parentIds = listOf(parentId),
                page = page,
                pageSize = size,
                includeArchived = includeArchived.takeIf { v -> v },
            )
        }.map { it.sortedWith(sortComparator(ItemSortOrder.NAME_ASC)) }
        return result.fold(
            // Feed children into the cache too, so a location's contents are browsable offline.
            onSuccess = { children -> cacheEntities(children); Result.success(children) },
            onFailure = { e ->
                val cached = cachedChildren(parentId, includeArchived).filter { !it.isLocation }
                if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
            },
        )
    }

    /**
     * Direct sub-locations of a location. A separate call from [getChildren] because the entities
     * endpoint returns items only unless isLocation=true is passed, so a location's child locations
     * never come back with its items. Offline, both kinds are in the cache, so we filter there.
     */
    suspend fun getChildLocations(
        parentId: String,
        includeArchived: Boolean = false,
    ): Result<List<EntitySummary>> {
        val result = allPages { api, page, size ->
            api.getEntities(
                parentIds = listOf(parentId),
                isLocation = true,
                page = page,
                pageSize = size,
                includeArchived = includeArchived.takeIf { v -> v },
            )
        }.map { it.sortedWith(sortComparator(ItemSortOrder.NAME_ASC)) }
        return result.fold(
            onSuccess = { children -> cacheEntities(children); Result.success(children) },
            onFailure = { e ->
                val cached = cachedChildren(parentId, includeArchived).filter { it.isLocation }
                if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
            },
        )
    }

    /**
     * Caches the whole collection's text for offline use: every item and location (summaries, with
     * parent links so a location's contents list offline), plus the full detail of anything whose
     * detail isn't already current. Incremental - only entities whose updatedAt changed are
     * re-fetched - so only the first run is heavy. [onEntitySynced] fires for each entity whose
     * detail was (re)fetched, so the caller can warm its thumbnail. Best-effort and silent; a no-op
     * when the cache isn't wired.
     *
     * Because the full set is fetched (and the pass is abandoned if any page fails), anything still
     * cached for this server and collection but no longer on the server was deleted there, so it is
     * pruned from the cache - otherwise deleted items would linger in offline lists and search.
     */
    suspend fun syncOfflineCache(onEntitySynced: suspend (EntitySummary) -> Unit = {}) {
        val details = detailCache ?: return
        val entities = entityCache ?: return
        val server = cacheServer()
        val tenant = cacheTenant()
        val items = allPages(maxPages = SYNC_MAX_PAGES) { api, page, size ->
            api.getEntities(page = page, pageSize = size, isLocation = false, includeArchived = true)
        }.getOrNull() ?: return
        val locations = allPages { api, page, size ->
            api.getEntities(page = page, pageSize = size, isLocation = true, includeArchived = true)
        }.getOrNull() ?: return
        val all = items + locations
        // The server or collection changed while paging: the rows may be a mix, so don't keep them.
        if (cacheServer() != server || cacheTenant() != tenant) return
        cacheEntities(all, server, tenant)
        pruneDeleted(entities, details, server, tenant, keep = all.mapNotNull { it.id }.toSet())
        for (summary in all) {
            val id = summary.id ?: continue
            val stamp = details.stamp(id)
            if (stamp == null || stamp != summary.updatedAt.orEmpty()) {
                val entity = call { it.getEntity(id) }.getOrNull()
                if (entity != null) {
                    cacheDetail(entity)
                    runCatchingCancellable { onEntitySynced(summary) }
                }
            }
        }
    }

    suspend fun getEntity(id: String): Result<EntityOut> {
        val result = call { it.getEntity(id) }
        return result.fold(
            onSuccess = { entity -> cacheDetail(entity); Result.success(entity) },
            // Offline: show the last-synced detail instead of a 504.
            onFailure = { e -> cachedDetail(id)?.let { Result.success(it) } ?: Result.failure(e) },
        )
    }

    suspend fun createEntity(body: EntityCreate): Result<EntityOut> = call { it.createEntity(body) }

    suspend fun updateEntity(id: String, body: EntityUpdate): Result<EntityOut> =
        call { it.updateEntity(id, body) }

    /**
     * Saves an edit without overwriting changes made on the server since the form loaded: fetches
     * the entity fresh, applies only the fields that differ between [base] and [edited] (see
     * [mergeEdit]), and sends the result as the full-replace update.
     */
    suspend fun mergeUpdateEntity(id: String, base: EntityUpdate, edited: EntityUpdate): Result<EntityOut> {
        val current = call { it.getEntity(id) }.getOrElse { return Result.failure(it) }
        return updateEntity(id, mergeEdit(base, edited, current.toUpdate().copy(id = id)))
    }

    /** Partial update - only the non-null fields of [body] are changed. */
    suspend fun patchEntity(id: String, body: EntityPatch): Result<EntityOut> =
        call { it.patchEntity(id, body) }

    suspend fun duplicateEntity(id: String, options: DuplicateOptions): Result<EntityOut> =
        call { it.duplicateEntity(id, options) }

    suspend fun deleteEntity(id: String): Result<Unit> =
        callUnit { it.deleteEntity(id) }.onSuccess { uncache(id) }

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

    /**
     * Downloads an attachment to [dest] using the authenticated client, streaming it to disk so a
     * large file (a manual PDF, a video) is never held in memory. A failed download leaves no
     * partial file behind.
     */
    suspend fun downloadAttachmentTo(entityId: String, attachmentId: String, dest: File): Result<Unit> =
        runCatchingCancellable {
            val url = attachmentUrl(entityId, attachmentId)
            val client = apiProvider.authenticatedImageClient()
            withContext(Dispatchers.IO) {
                try {
                    client.newCall(Request.Builder().url(url).build()).execute().use {
                        if (!it.isSuccessful) throw IllegalStateException("Download failed (${it.code}).")
                        dest.outputStream().use { out -> it.body.byteStream().copyTo(out) }
                    }
                } catch (e: Exception) {
                    dest.delete()
                    throw e
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
        runCatchingCancellable {
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

    /**
     * Uploads [file] as an attachment on an entity, streamed from disk rather than loaded into
     * memory. [fileName] is the name the server stores; [type] is e.g. "photo" or "attachment".
     */
    suspend fun uploadAttachment(
        entityId: String,
        file: File,
        fileName: String,
        mimeType: String?,
        type: String = "attachment",
        primary: Boolean = false,
    ): Result<Unit> = callUnit { api ->
        val media = (mimeType ?: "application/octet-stream").toMediaTypeOrNull()
        val parts = listOf(
            MultipartBody.Part.createFormData("file", fileName, file.asRequestBody(media)),
            MultipartBody.Part.createFormData("type", type),
            MultipartBody.Part.createFormData("name", fileName),
            MultipartBody.Part.createFormData("primary", primary.toString()),
        )
        api.uploadAttachment(entityId, parts)
    }

    // ---- CSV import / export ----
    /** Downloads the group's entities as CSV bytes (text/csv). */
    suspend fun exportCsv(): Result<ByteArray> = runCatchingCancellable {
        val response = api().exportEntities()
        if (response.isSuccessful) {
            (response.body() ?: throw IllegalStateException("Empty response from server."))
                .use { it.bytes() }
        } else {
            throw mapError(response.code(), response.errorBody()?.string())
        }
    }

    /** Uploads a Homebox CSV to create/update entities; [body] streams the file's contents. */
    suspend fun importCsv(fileName: String, body: RequestBody): Result<Unit> = callUnit { api ->
        val part = MultipartBody.Part.createFormData("csv", fileName, body)
        api.importEntities(part)
    }

    /** Downloads the bill of materials for the active collection as CSV bytes (text/csv). */
    suspend fun billOfMaterialsCsv(): Result<ByteArray> = runCatchingCancellable {
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
    suspend fun downloadExportTo(id: String, sink: OutputStream): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            val response = api().downloadExport(id)
            if (!response.isSuccessful) throw mapError(response.code(), response.errorBody()?.string())
            val body = response.body() ?: throw IllegalStateException("Empty response from server.")
            body.byteStream().use { input -> input.copyTo(sink) }
        }
    }

    /**
     * Restores a whole collection from a previously exported backup zip. [body] streams the zip, so
     * a backup with many photos isn't read into memory first.
     */
    suspend fun importCollection(fileName: String, body: RequestBody): Result<ExportOut> =
        call { api ->
            val part = MultipartBody.Part.createFormData("file", fileName, body)
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

    /** Drops the offline inventory cached for a saved server (e.g. after that server is deleted). */
    suspend fun clearCachedServer(serverId: String) {
        runCatchingCancellable { entityCache?.clearServer(serverId) }
    }

    /** Clears the saved session so the user returns to a disconnected state. */
    suspend fun clearSession() {
        val cfg = apiProvider.currentConfig()
        // Best-effort server-side logout for token sessions; ignore failures.
        runCatchingCancellable { if (cfg.token.isNotBlank()) logout() }
    }

    private companion object {
        /** Page size for full listings. */
        const val PAGE_SIZE = 200

        /** Safety cap on pages for a full listing (PAGE_SIZE x MAX_PAGES entities). */
        const val MAX_PAGES = 100

        /** The background sync walks further, so very large collections are still fully cached. */
        const val SYNC_MAX_PAGES = 200

        /** Most results an offline (cached) item search returns. */
        const val OFFLINE_SEARCH_LIMIT = 500

        /** Page size for pickers that load more as the user scrolls. */
        const val PICKER_PAGE_SIZE = 50
    }

    private fun sortComparator(sort: ItemSortOrder): Comparator<EntitySummary> = when (sort) {
        ItemSortOrder.NAME_ASC -> compareBy { it.displayName.lowercase() }
        ItemSortOrder.NAME_DESC -> compareByDescending { it.displayName.lowercase() }
        ItemSortOrder.NEWEST_FIRST -> compareByDescending { it.createdAt.orEmpty() }
        ItemSortOrder.OLDEST_FIRST -> compareBy { it.createdAt.orEmpty() }
    }
}

/** One page of [items] from [HomeboxRepository.getItemsPage]; [hasMore] says whether another page follows. */
data class ItemPage(val items: List<EntitySummary>, val hasMore: Boolean)
