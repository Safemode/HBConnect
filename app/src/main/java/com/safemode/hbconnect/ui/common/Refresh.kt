package com.safemode.hbconnect.ui.common

import android.content.Context
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi

/**
 * Clears Coil's in-memory and on-disk image caches so the next image request
 * re-downloads from the server. Used by pull-to-refresh.
 */
@OptIn(ExperimentalCoilApi::class)
fun clearImageCache(context: Context) {
    val loader = SingletonImageLoader.get(context)
    loader.memoryCache?.clear()
    loader.diskCache?.clear()
}
