package com.safemode.hbconnect.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.safemode.hbconnect.ui.theme.HBConnectTheme
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Which symbologies the scanner should look for. */
enum class ScanMode(val formats: List<BarcodeFormat>) {
    /** QR codes only - used for Homebox labels on the dashboard. */
    QR(listOf(BarcodeFormat.QR_CODE)),

    /** 1D product codes - used for UPC/EAN lookup on the new-item screen. */
    PRODUCT(
        listOf(
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_8,
            BarcodeFormat.EAN_13,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
        ),
    ),
}

/** Input for [ScanBarcodeContract]: what to scan for and the on-screen prompt. */
data class ScanRequest(val mode: ScanMode, val prompt: String)

/**
 * Launches the in-app CameraX scanner and returns the decoded text, or null if cancelled. Replaces
 * the old zxing-android-embedded ScanContract; decoding uses ZXing core, so no Google Play Services.
 */
class ScanBarcodeContract : ActivityResultContract<ScanRequest, String?>() {
    override fun createIntent(context: Context, input: ScanRequest): Intent =
        Intent(context, BarcodeScannerActivity::class.java).apply {
            putExtra(EXTRA_MODE, input.mode.name)
            putExtra(EXTRA_PROMPT, input.prompt)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_RESULT) else null

    companion object {
        const val EXTRA_MODE = "scan_mode"
        const val EXTRA_PROMPT = "scan_prompt"
        const val EXTRA_RESULT = "scan_result"
    }
}

/**
 * Host activity for the scanner. Deliberately not orientation-locked, so it rotates with the device
 * - portrait in portrait, landscape in landscape.
 */
class BarcodeScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = runCatching {
            ScanMode.valueOf(intent.getStringExtra(ScanBarcodeContract.EXTRA_MODE) ?: ScanMode.QR.name)
        }.getOrDefault(ScanMode.QR)
        val prompt = intent.getStringExtra(ScanBarcodeContract.EXTRA_PROMPT) ?: "Scan a code"
        setContent {
            HBConnectTheme {
                BarcodeScannerScreen(
                    mode = mode,
                    prompt = prompt,
                    onResult = { text ->
                        setResult(RESULT_OK, Intent().putExtra(ScanBarcodeContract.EXTRA_RESULT, text))
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun BarcodeScannerScreen(
    mode: ScanMode,
    prompt: String,
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) onCancel()
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        PermissionPrompt(onCancel = onCancel)
        return
    }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Guards against firing onResult more than once as frames keep arriving after the first hit.
    val handled = remember { booleanArrayOf(false) }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(Unit) {
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val decoder = BarcodeDecoder(mode.formats)
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            val text = try {
                decoder.decode(proxy)
            } catch (_: Throwable) {
                null
            } finally {
                proxy.close()
            }
            if (text != null && !handled[0]) {
                handled[0] = true
                ContextCompat.getMainExecutor(context).execute { onResult(text) }
            }
        }
        val preview = Preview.Builder().build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        runCatching {
            val provider = context.awaitCameraProvider()
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }.onSuccess { camera = it }.onFailure { error = "Couldn't start the camera." }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tapping the preview focuses there.
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        val cam = camera ?: return@detectTapGestures
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        runCatching {
                            cam.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(point).build(),
                            )
                        }
                    }
                },
        )

        // Reticle to aim with.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
        )

        Text(
            text = prompt,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(top = 48.dp),
        )

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            FilledIconButton(
                onClick = {
                    torchOn = !torchOn
                    runCatching { camera?.cameraControl?.enableTorch(torchOn) }
                },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
            ) {
                Icon(
                    if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                )
            }
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Camera access is needed to scan a code.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onCancel) { Text("Close") }
        }
    }
}

/** Decodes a camera frame's luminance with ZXing core. Reused across frames on the analysis thread. */
private class BarcodeDecoder(formats: List<BarcodeFormat>) {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to formats,
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    fun decode(image: ImageProxy): String? {
        val luminance = image.planes[0].toLuminance(image.width, image.height)
        // The analysis buffer is in sensor orientation, so upright it first (ZXing's 1D readers only
        // scan horizontal rows). If nothing is found, try a further 90° turn so a product barcode
        // reads whether it's horizontal or vertical on screen. QR is rotation-invariant either way.
        val (upright, w, h) = rotateLuminance(
            luminance, image.width, image.height, image.imageInfo.rotationDegrees,
        )
        decodeLuminance(upright, w, h)?.let { return it }
        val (turned, tw, th) = rotateLuminance(upright, w, h, 90)
        return decodeLuminance(turned, tw, th)
    }

    private fun decodeLuminance(data: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap).text
        } catch (_: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }
}

/**
 * Rotates a tightly-packed luminance buffer by [degrees] (0/90/180/270, clockwise). Returns the
 * rotated bytes and the new width/height (swapped for 90°/270°).
 */
private fun rotateLuminance(src: ByteArray, width: Int, height: Int, degrees: Int): Triple<ByteArray, Int, Int> {
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
private fun ImageProxy.PlaneProxy.toLuminance(width: Int, height: Int): ByteArray {
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
private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }
