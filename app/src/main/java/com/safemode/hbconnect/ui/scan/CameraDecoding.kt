package com.safemode.hbconnect.ui.scan

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ResultPoint
import com.google.zxing.common.HybridBinarizer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Shared CameraX + ZXing decoding used by both the one-shot [BarcodeScannerActivity] and the AR
 * overlay scanner. Kept in one place so the frame-rotation and luminance-packing logic (which is
 * fiddly to get right) has a single implementation.
 */

/**
 * A decoded code. [nx]/[ny] are the code's centre as a fraction (0..1) of the upright camera frame -
 * the AR overlay uses them to pin a label over the physical code. [frameW]/[frameH] are that upright
 * frame's dimensions, so the overlay can account for the preview's FILL_CENTER cropping. The one-shot
 * scanner ignores everything but [text].
 */
data class DecodeResult(
    val text: String,
    val nx: Float,
    val ny: Float,
    val frameW: Int,
    val frameH: Int,
)

/**
 * Decodes one camera frame's luminance with ZXing core (reused across frames on the analysis thread).
 * Returns the decoded text plus where the code sits in the frame, or null if nothing was found.
 */
class BarcodeDecoder(formats: List<BarcodeFormat>) {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to formats,
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    fun decode(image: ImageProxy): DecodeResult? {
        val luminance = image.planes[0].toLuminance(image.width, image.height)
        // The analysis buffer is in sensor orientation, so upright it first (ZXing's 1D readers only
        // scan horizontal rows). If nothing is found, try a further 90 degree turn so a product
        // barcode reads whether it's horizontal or vertical on screen. QR is rotation-invariant.
        val (upright, w, h) = rotateLuminance(
            luminance, image.width, image.height, image.imageInfo.rotationDegrees,
        )
        decodeLuminance(upright, w, h)?.let { (text, points) ->
            val (cx, cy) = centreOf(points, w, h)
            return DecodeResult(text, cx / w, cy / h, w, h)
        }
        val (turned, tw, th) = rotateLuminance(upright, w, h, 90)
        decodeLuminance(turned, tw, th)?.let { (text, points) ->
            // Map the centre from the twice-turned buffer back into the upright frame so the overlay
            // still anchors correctly: a turned point (tx, ty) came from upright (ty, h - 1 - tx).
            val (tcx, tcy) = centreOf(points, tw, th)
            val ux = tcy
            val uy = h - 1 - tcx
            return DecodeResult(text, ux / w, uy / h, w, h)
        }
        return null
    }

    /** Decodes [data], returning the text and the code's locator points, or null if not found. */
    private fun decodeLuminance(data: ByteArray, width: Int, height: Int): Pair<String, Array<ResultPoint>>? {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = reader.decodeWithState(bitmap)
            result.text to (result.resultPoints ?: emptyArray())
        } catch (_: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }
}

/** Average of a code's locator points, falling back to the frame centre when none are reported. */
private fun centreOf(points: Array<ResultPoint>, width: Int, height: Int): Pair<Float, Float> {
    if (points.isEmpty()) return width / 2f to height / 2f
    var sx = 0f
    var sy = 0f
    for (p in points) {
        sx += p.x
        sy += p.y
    }
    return sx / points.size to sy / points.size
}

/**
 * Rotates a tightly-packed luminance buffer by [degrees] (0/90/180/270, clockwise). Returns the
 * rotated bytes and the new width/height (swapped for 90/270).
 */
internal fun rotateLuminance(src: ByteArray, width: Int, height: Int, degrees: Int): Triple<ByteArray, Int, Int> {
    val d = ((degrees % 360) + 360) % 360
    if (d == 0) return Triple(src, width, height)
    val out = ByteArray(src.size)
    when (d) {
        90 -> {
            for (y in 0 until height) for (x in 0 until width) {
                out[x * height + (height - 1 - y)] = src[y * width + x]
            }
            return Triple(out, height, width)
        }
        180 -> {
            for (y in 0 until height) for (x in 0 until width) {
                out[(height - 1 - y) * width + (width - 1 - x)] = src[y * width + x]
            }
            return Triple(out, width, height)
        }
        else -> { // 270
            for (y in 0 until height) for (x in 0 until width) {
                out[(width - 1 - x) * height + y] = src[y * width + x]
            }
            return Triple(out, height, width)
        }
    }
}

/** Copies the Y (luminance) plane into a tightly-packed width*height byte array, honoring strides. */
internal fun ImageProxy.PlaneProxy.toLuminance(width: Int, height: Int): ByteArray {
    val buffer = buffer.also { it.rewind() }
    val out = ByteArray(width * height)
    if (pixelStride == 1 && rowStride == width) {
        buffer.get(out, 0, width * height)
        return out
    }
    val rowBytes = ByteArray(rowStride)
    var outPos = 0
    for (row in 0 until height) {
        buffer.position(row * rowStride)
        val available = minOf(rowStride, buffer.remaining())
        buffer.get(rowBytes, 0, available)
        if (pixelStride == 1) {
            System.arraycopy(rowBytes, 0, out, outPos, width)
        } else {
            for (col in 0 until width) out[outPos + col] = rowBytes[col * pixelStride]
        }
        outPos += width
    }
    return out
}

/** Awaits the CameraX provider without blocking the main thread. */
internal suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }
