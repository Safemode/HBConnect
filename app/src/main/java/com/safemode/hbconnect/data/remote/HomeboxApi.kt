package com.safemode.hbconnect.data.remote

import com.safemode.hbconnect.data.remote.models.ApiSummary
import com.safemode.hbconnect.data.remote.models.AttachmentUpdate
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.DuplicateOptions
import com.safemode.hbconnect.data.remote.models.EntityCreate
import com.safemode.hbconnect.data.remote.models.EntityListResult
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntityPatch
import com.safemode.hbconnect.data.remote.models.EntityPath
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.data.remote.models.EntityUpdate
import com.safemode.hbconnect.data.remote.models.Group
import com.safemode.hbconnect.data.remote.models.GroupStatistics
import com.safemode.hbconnect.data.remote.models.LoginForm
import com.safemode.hbconnect.data.remote.models.TagCreate
import com.safemode.hbconnect.data.remote.models.TagOut
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.data.remote.models.TagUpdate
import com.safemode.hbconnect.data.remote.models.TokenResponse
import com.safemode.hbconnect.data.remote.models.TotalsByOrganizer
import com.safemode.hbconnect.data.remote.models.TreeItem
import com.safemode.hbconnect.data.remote.models.ValueOverTime
import com.safemode.hbconnect.data.remote.models.UserSelfResponse
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import com.safemode.hbconnect.data.remote.models.MaintenanceEntryRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Homebox REST API (v0.26+, "entities" model, verified against
 * the v0.26.2 OpenAPI schema). The base path is `/api`; the `Authorization: Bearer …` header is
 * added per request by [AuthInterceptor], so it is not declared here.
 */
interface HomeboxApi {

    // ---- System / auth ----
    @GET("api/v1/status")
    suspend fun status(): Response<ApiSummary>

    @POST("api/v1/users/login")
    suspend fun login(@Body body: LoginForm): Response<TokenResponse>

    @GET("api/v1/users/refresh")
    suspend fun refresh(): Response<TokenResponse>

    @POST("api/v1/users/logout")
    suspend fun logout(): Response<ResponseBody>

    @GET("api/v1/users/self")
    suspend fun self(): Response<UserSelfResponse>

    @GET("api/v1/groups/statistics")
    suspend fun statistics(): Response<GroupStatistics>

    /** Summed purchase price grouped by location (a value total, not an item count). */
    @GET("api/v1/groups/statistics/locations")
    suspend fun statsByLocation(): Response<List<TotalsByOrganizer>>

    /** Summed purchase price grouped by tag (a value total, not an item count). */
    @GET("api/v1/groups/statistics/tags")
    suspend fun statsByTag(): Response<List<TotalsByOrganizer>>

    /** Priced items created within [start]..[end] (yyyy-MM-dd), for a cumulative value line. */
    @GET("api/v1/groups/statistics/purchase-price")
    suspend fun statsValueOverTime(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
    ): Response<ValueOverTime>

    /** The signed-in user's group, including its configured currency code. */
    @GET("api/v1/groups")
    suspend fun group(): Response<Group>

    /** Supported currencies and their symbol/decimal metadata. */
    @GET("api/v1/currencies")
    suspend fun currencies(): Response<List<Currency>>

    // ---- Entities (items + locations) ----
    @GET("api/v1/entities")
    suspend fun getEntities(
        @Query("q") q: String? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("tags") tags: List<String>? = null,
        @Query("parentIds") parentIds: List<String>? = null,
        // "true" = locations only, "false" = items only, null = server default (items only).
        @Query("isLocation") isLocation: Boolean? = null,
        @Query("includeArchived") includeArchived: Boolean? = null,
        @Query("negateTags") negateTags: Boolean? = null,
        @Query("onlyWithPhoto") onlyWithPhoto: Boolean? = null,
        @Query("onlyWithoutPhoto") onlyWithoutPhoto: Boolean? = null,
        // Each entry is a "Name=Value" custom-field match; repeated for multiple fields.
        @Query("fields") fields: List<String>? = null,
        // orderBy: name | createdAt | updatedAt | assetId. orderDirection: asc | desc.
        @Query("orderBy") orderBy: String? = null,
        @Query("orderDirection") orderDirection: String? = null,
    ): Response<EntityListResult>

    /** Distinct custom-field names defined across the group's entities. */
    @GET("api/v1/entities/fields")
    suspend fun getCustomFieldNames(): Response<List<String>>

    /** Distinct values recorded for a given custom-field name. */
    @GET("api/v1/entities/fields/values")
    suspend fun getCustomFieldValues(@Query("field") field: String): Response<List<String>>

    @GET("api/v1/entities/{id}")
    suspend fun getEntity(@Path("id") id: String): Response<EntityOut>

    @POST("api/v1/entities")
    suspend fun createEntity(@Body body: EntityCreate): Response<EntityOut>

    @PUT("api/v1/entities/{id}")
    suspend fun updateEntity(@Path("id") id: String, @Body body: EntityUpdate): Response<EntityOut>

    /** Partial update; omitted/null fields are left unchanged. */
    @PATCH("api/v1/entities/{id}")
    suspend fun patchEntity(@Path("id") id: String, @Body body: EntityPatch): Response<EntityOut>

    @POST("api/v1/entities/{id}/duplicate")
    suspend fun duplicateEntity(@Path("id") id: String, @Body body: DuplicateOptions): Response<EntityOut>

    @DELETE("api/v1/entities/{id}")
    suspend fun deleteEntity(@Path("id") id: String): Response<ResponseBody>

    /** Look up items by asset id (digits; dashes are ignored server-side). */
    @GET("api/v1/assets/{id}")
    suspend fun getByAssetId(@Path("id") assetId: String): Response<EntityListResult>

    @GET("api/v1/entities/tree")
    suspend fun getTree(@Query("withItems") withItems: Boolean = false): Response<List<TreeItem>>

    /** Ancestor path (root-first, ending with the entity itself). */
    @GET("api/v1/entities/{id}/path")
    suspend fun getEntityPath(@Path("id") id: String): Response<List<EntityPath>>

    // ---- Entity types ----
    @GET("api/v1/entity-types")
    suspend fun getEntityTypes(): Response<List<EntityTypeSummary>>

    // ---- Tags ----
    @GET("api/v1/tags")
    suspend fun getTags(): Response<List<TagSummary>>

    @GET("api/v1/tags/{id}")
    suspend fun getTag(@Path("id") id: String): Response<TagOut>

    @POST("api/v1/tags")
    suspend fun createTag(@Body body: TagCreate): Response<TagOut>

    @PUT("api/v1/tags/{id}")
    suspend fun updateTag(@Path("id") id: String, @Body body: TagUpdate): Response<TagOut>

    @DELETE("api/v1/tags/{id}")
    suspend fun deleteTag(@Path("id") id: String): Response<ResponseBody>

    // ---- Attachments ----
    @Multipart
    @POST("api/v1/entities/{id}/attachments")
    suspend fun uploadAttachment(
        @Path("id") id: String,
        @Part parts: List<MultipartBody.Part>,
    ): Response<EntityOut>

    @PUT("api/v1/entities/{id}/attachments/{attachmentId}")
    suspend fun updateAttachment(
        @Path("id") id: String,
        @Path("attachmentId") attachmentId: String,
        @Body body: AttachmentUpdate,
    ): Response<EntityOut>

    @DELETE("api/v1/entities/{id}/attachments/{attachmentId}")
    suspend fun deleteAttachment(
        @Path("id") id: String,
        @Path("attachmentId") attachmentId: String,
    ): Response<ResponseBody>

    // ---- Maintenance ----
    @GET("api/v1/entities/{id}/maintenance")
    suspend fun getMaintenance(
        @Path("id") id: String,
        @Query("status") status: String? = null,
    ): Response<List<MaintenanceEntry>>

    /** Group-wide maintenance. [status] is required: scheduled | completed | both. */
    @GET("api/v1/maintenance")
    suspend fun getAllMaintenance(@Query("status") status: String): Response<List<MaintenanceEntry>>

    @POST("api/v1/entities/{id}/maintenance")
    suspend fun createMaintenance(
        @Path("id") id: String,
        @Body body: MaintenanceEntryRequest,
    ): Response<MaintenanceEntry>

    @PUT("api/v1/maintenance/{id}")
    suspend fun updateMaintenance(
        @Path("id") id: String,
        @Body body: MaintenanceEntryRequest,
    ): Response<MaintenanceEntry>

    @DELETE("api/v1/maintenance/{id}")
    suspend fun deleteMaintenance(@Path("id") id: String): Response<ResponseBody>
}
