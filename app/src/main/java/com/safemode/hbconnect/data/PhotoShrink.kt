package com.safemode.hbconnect.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest edge, in pixels, a photo is reduced to before upload. */
const val MAX_PHOTO_EDGE = 2048

/** A photo at or under this size and [MAX_PHOTO_EDGE] is uploaded as it is. */
private const val SMALL_PHOTO_BYTES = 1_500_000L

private const val JPEG_QUALITY = 85

/**
 * The power-of-two decode step that keeps the longest edge at or above [maxEdge], so the decoded
 * bitmap is as small as possible without losing detail the final resize needs.
 */
fun photoSampleSize(width: Int, height: Int, maxEdge: Int = MAX_PHOTO_EDGE): Int {
    var sample = 1
    val edge = max(width, height)
    while (edge / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

/**
 * Shrinks a camera or picked photo before it is uploaded, so a 4-12 MB phone photo doesn't hit a
 * reverse proxy's upload limit or fill the server: the longest edge is reduced to [MAX_PHOTO_EDGE],
 * the camera's rotation is applied to the pixels, and the result is re-encoded (JPEG, or PNG for a
 * PNG). Re-encoding also drops the original's metadata, including any GPS location. Returns the
 * path to upload: [path] itself when the photo is already small, is a GIF (possibly animated), or
 * can't be decoded; otherwise a new file next to it (the original is deleted). Blocking - call off
 * the main thread.
 */
fun shrinkPhoto(path: String): String = runCatching {
    val source = File(path)
    val mime = imageMimeType(source.name)
    if (mime == "image/gif" || !source.isFile) return path

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return path
    if (max(width, height) <= MAX_PHOTO_EDGE && source.length() <= SMALL_PHOTO_BYTES) return path

    val decoded = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = photoSampleSize(width, height) },
    ) ?: return path
    val oriented = applyExifRotation(decoded, path)
    val scale = MAX_PHOTO_EDGE.toFloat() / max(oriented.width, oriented.height)
    val resized = if (scale < 1f) {
        oriented.scale(
            (oriented.width * scale).roundToInt().coerceAtLeast(1),
            (oriented.height * scale).roundToInt().coerceAtLeast(1),
        )
    } else {
        oriented
    }

    val png = mime == "image/png"
    val out = File(source.parentFile, source.nameWithoutExtension + "_s." + if (png) "png" else "jpg")
    out.outputStream().use { stream ->
        val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        check(resized.compress(format, JPEG_QUALITY, stream)) { "Couldn't encode the photo." }
    }
    if (resized !== oriented) resized.recycle()
    if (oriented !== decoded) oriented.recycle()
    decoded.recycle()
    source.delete()
    out.absolutePath
}.getOrDefault(path)

/** Rotates or flips [bitmap] as the photo's EXIF orientation says, since re-encoding drops that tag. */
private fun applyExifRotation(bitmap: Bitmap, path: String): Bitmap {
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
