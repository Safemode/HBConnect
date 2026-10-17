package com.safemode.hbconnect.data.remote

import com.safemode.hbconnect.data.settings.ConnectionConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Builds and caches a [HomeboxApi] bound to the current [ConnectionConfig]. Retrofit requires an
 * absolute base URL, so the instance is rebuilt whenever the host/port/scheme change. The bearer
 * auth header is injected per request from the latest config, so credential-only changes don't
 * require a rebuild.
 *
 * GET responses are stored in an on-disk HTTP cache so the app can serve data when the server is
 * unreachable; [serverStatus] is updated by [OfflineInterceptor] on each call.
 */
class ApiProvider(
    private val cacheDir: File,
    val serverStatus: ServerStatus,
    private val isOnline: () -> Boolean,
) {

    @Volatile
    private var config: ConnectionConfig = ConnectionConfig()

    @Volatile
    private var cachedApi: HomeboxApi? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var httpCache: Cache = Cache(File(cacheDir, HTTP_CACHE_DIR), config.cacheSize.bytes)

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /** A single long-lived client for reachability probes, reused across every probe. */
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { config })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun updateConfig(newConfig: ConnectionConfig) {
        val sizeChanged = newConfig.cacheSize != config.cacheSize
        config = newConfig
        if (sizeChanged) resizeCache(newConfig.cacheSize.bytes)
    }

    /** Latest known connection config (for components like the image loader). */
    fun currentConfig(): ConnectionConfig = config

    /** Current on-disk cache usage in bytes (0 if it can't be read). */
    fun cacheUsageBytes(): Long = runCatching { httpCache.size() }.getOrDefault(0L)

    /** Removes all cached responses. */
    fun clearCache() {
        runCatching { httpCache.evictAll() }
    }

    /**
     * A direct, short-timeout network hit that bypasses the cache/offline interceptor, used by the
     * background reachability check. Hits the public `/api/v1/status` endpoint. Blocking - call off
     * the main thread. Returns true if the server responds at all (any HTTP status).
     */
    fun probeReachable(): Boolean {
        val cfg = config
        if (cfg.host.isBlank()) return false
        val request = Request.Builder()
            .url(cfg.baseUrl() + "api/v1/status")
            .get()
            .build()
        return try {
            probeClient.newCall(request).execute().use { true }
        } catch (e: IOException) {
            false
        }
    }

    /** Recreates the cache with a new size limit (OkHttp's cache size is fixed at creation). */
    @Synchronized
    private fun resizeCache(maxBytes: Long) {
        runCatching { httpCache.close() }
        httpCache = Cache(File(cacheDir, HTTP_CACHE_DIR), maxBytes)
        cachedApi = null
        cachedBaseUrl = null
    }

    /**
     * An OkHttp client that injects only the auth header (no JSON Accept header), so protected
     * item images/attachments can be fetched by Coil. Follows redirects so presigned storage URLs
     * resolve.
     */
    fun authenticatedImageClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val builder = chain.request().newBuilder()
            config.authHeader()?.let { builder.header("Authorization", it) }
            config.tenantHeader()?.let { builder.header("X-Tenant", it) }
            chain.proceed(builder.build())
        })
        .followRedirects(true)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Current API, or null when the host isn't set yet. */
    fun apiOrNull(): HomeboxApi? {
        val current = config
        if (current.host.isBlank()) return null
        val baseUrl = current.baseUrl()
        val existing = cachedApi
        if (existing != null && cachedBaseUrl == baseUrl) return existing

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val api = retrofit.create(HomeboxApi::class.java)
        cachedApi = api
        cachedBaseUrl = baseUrl
        return api
    }

    private fun buildClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .cache(httpCache)
            .addInterceptor(AuthInterceptor { config })
            // Reports reachability and falls back to cached data when the server is down.
            .addInterceptor(OfflineInterceptor(serverStatus, isOnline))
            .addNetworkInterceptor(CacheWriteInterceptor())
            .addInterceptor(logging)
            .retryOnConnectionFailure(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private companion object {
        const val HTTP_CACHE_DIR = "http-cache"
    }
}

/** Adds the bearer authorization header using the latest config. */
class AuthInterceptor(private val configProvider: () -> ConnectionConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val config = configProvider()
        val request = chain.request()
        val builder = request.newBuilder()
        config.authHeader()?.let { builder.header("Authorization", it) }
        // A request may target a specific collection via its own X-Tenant header (e.g. renaming a
        // non-active collection); only fall back to the active collection when it set none.
        if (request.header("X-Tenant") == null) {
            config.tenantHeader()?.let { builder.header("X-Tenant", it) }
        }
        builder.header("Accept", "application/json")
        return chain.proceed(builder.build())
    }
}

/**
 * Tries the network first; on a connection failure it reports the server as unreachable and
 * retries the request against the cache only (serving stale data when available). A successful
 * network call reports the server as reachable. Writes (non-GET) simply fail offline.
 */
class OfflineInterceptor(
    private val status: ServerStatus,
    private val isOnline: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isGet = request.method == "GET"
        if (isGet && (!isOnline() || !status.isReachable())) {
            return chain.proceed(request.cacheOnly())
        }
        return try {
            val response = chain.proceed(request)
            status.report(true)
            response
        } catch (e: IOException) {
            status.report(false)
            chain.proceed(request.cacheOnly())
        }
    }

    private fun Request.cacheOnly(): Request = newBuilder()
        .cacheControl(
            CacheControl.Builder()
                .onlyIfCached()
                .maxStale(Int.MAX_VALUE, TimeUnit.SECONDS)
                .build(),
        )
        .build()
}

/**
 * Rewrites response cache headers so GET responses are stored on disk, while `max-age=0` keeps
 * them immediately stale - so online requests always re-fetch fresh data and only the offline path
 * (only-if-cached + max-stale) serves the stored copy.
 */
class CacheWriteInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Cache-Control")
            .header("Cache-Control", "public, max-age=0")
            .build()
    }
}
