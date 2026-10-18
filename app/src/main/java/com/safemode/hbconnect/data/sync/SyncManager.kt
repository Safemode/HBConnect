package com.safemode.hbconnect.data.sync

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.safemode.hbconnect.data.HomeboxRepository
import com.safemode.hbconnect.data.remote.ServerStatus
import com.safemode.hbconnect.data.remote.models.EntitySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Silently caches the collection for offline use. On launch and on each reconnect it pulls every
 * item and location's text into the local database and warms each entity's thumbnail into Coil's
 * (size-capped) image cache, so lists, detail screens, a location's contents, and search all work
 * when the server is unreachable. It's incremental and best-effort: only changed entities are
 * re-fetched, failures are swallowed, and at most one pass runs at a time.
 *
 * Thumbnails go into Coil's disk cache rather than the SQL database on purpose: image blobs bloat
 * SQLite, whereas Coil's cache is purpose-built, LRU-bounded (so it can't grow without limit), and
 * is already what the UI reads from - warming it is what makes the existing image URLs resolve
 * offline. Only the small thumbnail variant is fetched, not the full-size photo.
 */
class SyncManager(
    private val repository: HomeboxRepository,
    private val context: Context,
    private val serverStatus: ServerStatus,
    private val isOnline: () -> Boolean,
    private val scope: CoroutineScope,
) {
    private val running = AtomicBoolean(false)

    /** Starts a background sync if the server is reachable and one isn't already running. */
    fun requestSync() {
        if (!isOnline() || !serverStatus.isReachable()) return
        if (!running.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                repository.syncOfflineCache { summary -> warmThumbnail(summary) }
            } catch (_: Throwable) {
                // Best-effort and silent; a failed sync just leaves the previous cache in place.
            } finally {
                running.set(false)
            }
        }
    }

    /** Pulls an entity's thumbnail through Coil so the small image is on disk for offline viewing. */
    private suspend fun warmThumbnail(summary: EntitySummary) {
        val id = summary.id ?: return
        val attachmentId = summary.thumbnailId?.ifBlank { null } ?: summary.imageId?.ifBlank { null } ?: return
        val url = repository.attachmentUrl(id, attachmentId)
        val loader: ImageLoader = SingletonImageLoader.get(context)
        runCatching { loader.execute(ImageRequest.Builder(context).data(url).build()) }
    }
}
