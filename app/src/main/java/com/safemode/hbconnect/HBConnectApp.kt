package com.safemode.hbconnect

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.safemode.hbconnect.notifications.MaintenanceNotifier
import okio.Path.Companion.toOkioPath

class HBConnectApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        MaintenanceNotifier.createChannel(this)
    }

    /**
     * The app-wide Coil image loader. Network images go through our own OkHttp client (which carries
     * the Homebox bearer and X-Tenant headers and follows redirects to presigned storage URLs),
     * supplied as an [OkHttpNetworkFetcherFactory] - Coil 3 removed `ImageLoader.Builder.okHttpClient`
     * and no longer bundles networking in coil-core, so the client is wired through the network
     * component instead. Adding the fetcher explicitly makes it take precedence over the default one
     * Coil would otherwise service-load.
     *
     * Images are cached in memory and on disk so each is fetched from the server only once; attachment
     * ids are stable UUIDs, so the URL is a safe cache key. Coil 3 ignores HTTP cache-control headers
     * by default (Coil 2 needed `respectCacheHeaders(false)` for this), which is the behaviour we want.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { Graph.apiProvider.authenticatedImageClient() }))
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .crossfade(true)
            .build()
}
