package com.safemode.hbconnect.ui.common

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader

/**
 * Clears Coil's in-memory and on-disk image caches so the next image request
 * re-downloads from the server. Used by pull-to-refresh.
 */
@OptIn(ExperimentalCoilApi::class)
fun clearImageCache(context: Context) {
    val loader = context.imageLoader
    loader.memoryCache?.clear()
    loader.diskCache?.clear()
}
