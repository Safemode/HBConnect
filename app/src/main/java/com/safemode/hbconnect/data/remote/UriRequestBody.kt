package com.safemode.hbconnect.data.remote

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

/**
 * A request body that streams a user-picked document straight from its content [uri], so large
 * files (a whole-collection backup zip, a big CSV) are sent without first being read into memory.
 * The stream is reopened on each write, so OkHttp can safely retry the request.
 */
class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    /** The document's size when the provider reports it, else -1 (sent chunked). */
    override fun contentLength(): Long =
        runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }
            .getOrNull()
            ?.takeIf { it >= 0 }
            ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the chosen file.")
        input.source().use { sink.writeAll(it) }
    }

    companion object {
        /**
         * Confirms [uri] can be opened before an upload starts. A read failure inside [writeTo]
         * would surface as a network error (and briefly mark the server unreachable), so callers
         * check first and report a plain "couldn't open" instead.
         */
        fun checkReadable(resolver: ContentResolver, uri: Uri) {
            (resolver.openInputStream(uri) ?: throw IOException("Couldn't open the chosen file.")).close()
        }
    }
}
