package com.safemode.hbconnect.data

/**
 * MIME type for a photo file, from its extension. A missing or unknown extension is treated as JPEG,
 * which is what the camera writes.
 */
fun imageMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "bmp" -> "image/bmp"
    else -> "image/jpeg"
}

/** File extension to save a picked image under, from its MIME type. The inverse of [imageMimeType]. */
fun imageExtension(mimeType: String?): String = when (mimeType?.lowercase()) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/heic" -> "heic"
    "image/heif" -> "heif"
    "image/bmp" -> "bmp"
    else -> "jpg"
}
