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
import androidx.camera.core.Preview
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.safemode.hbconnect.ui.theme.HBConnectTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

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

    /**
     * Any common 1D or 2D symbology - used to read a model or serial number printed on a box. The
     * decoded text is dropped straight into the field, so this casts a wide net rather than assuming
     * a product code.
     */
    IDENTIFIER(
        listOf(
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODABAR,
            BarcodeFormat.ITF,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_8,
            BarcodeFormat.EAN_13,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
        ),
    ),
}

/**
 * Input for [ScanBarcodeContract]: what to scan for, the on-screen prompt, and whether the AR overlay
 * mode may be toggled on. AR is only meaningful for Homebox labels (it resolves a code to an existing
 * item), so the product-barcode scan on the new-item screen leaves [allowAr] off.
 */
data class ScanRequest(val mode: ScanMode, val prompt: String, val allowAr: Boolean = false)

/**
 * Launches the in-app CameraX scanner and returns a string, or null if cancelled. In classic mode the
 * string is the decoded code; in AR mode it is the entity id of the card the user tapped (a bare UUID
 * the caller resolves the same way as a scanned label). Decoding uses ZXing core, so no Google Play
 * Services.
 */
class ScanBarcodeContract : ActivityResultContract<ScanRequest, String?>() {
    override fun createIntent(context: Context, input: ScanRequest): Intent =
        Intent(context, BarcodeScannerActivity::class.java).apply {
            putExtra(EXTRA_MODE, input.mode.name)
            putExtra(EXTRA_PROMPT, input.prompt)
            putExtra(EXTRA_ALLOW_AR, input.allowAr)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_RESULT) else null

    companion object {
        const val EXTRA_MODE = "scan_mode"
        const val EXTRA_PROMPT = "scan_prompt"
        const val EXTRA_ALLOW_AR = "scan_allow_ar"
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
        val allowAr = intent.getBooleanExtra(ScanBarcodeContract.EXTRA_ALLOW_AR, false)
        setContent {
            HBConnectTheme {
                BarcodeScannerScreen(
                    mode = mode,
                    prompt = prompt,
                    allowAr = allowAr,
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

/**
 * The scanner screen. It has two modes sharing one camera and decoder:
 * - **Classic** (the default): an aim reticle; the first decoded code returns and closes the scanner.
 * - **AR overlay** (only when [allowAr]): the camera stays live and floats an item card over each
 *   Homebox label it recognizes, pinned near the code; tapping a card returns that item's id.
 *
 * The AR mode is turned on and off with a toggle stacked above the torch button, so it is one button
 * away inside the same view rather than a separate scanner.
 */
@Composable
private fun BarcodeScannerScreen(
    mode: ScanMode,
    prompt: String,
    allowAr: Boolean,
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // Guards against firing onResult more than once as frames keep arriving after the first hit.
    val handled = remember { booleanArrayOf(false) }

    // AR mode state. Held as an explicit MutableState so the long-lived analyzer closure below can
    // read the current value each frame (it is created once and captures this object, not a snapshot).
    val arModeState = remember { mutableStateOf(false) }
    var arMode by arModeState

    // Codes seen within the TTL, keyed by their decoded text. Touched on the main thread only.
    val detections = remember { mutableStateMapOf<String, ArDetection>() }
    // Resolutions survive pruning, so re-seeing a code after it left view doesn't re-hit the network.
    val resolved = remember { mutableMapOf<String, Resolution>() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(Unit) {
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val decoder = BarcodeDecoder(mode.formats)
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            val result = try {
                decoder.decode(proxy)
            } catch (_: Throwable) {
                null
            } finally {
                proxy.close()
            }
            if (result != null) {
                // Hop to the main thread to touch Compose state (and start any AR lookup).
                ContextCompat.getMainExecutor(context).execute {
                    if (!arModeState.value) {
                        // Classic: first hit wins, return the raw code and close.
                        if (!handled[0]) {
                            handled[0] = true
                            onResult(result.text)
                        }
                        return@execute
                    }
                    // AR: track the code and overlay a card for it.
                    val now = System.currentTimeMillis()
                    val code = result.text
                    val existing = detections[code]
                    if (existing != null) {
                        // Still in view: refresh its position and keep-alive time.
                        detections[code] = existing.copy(
                            nx = result.nx, ny = result.ny,
                            frameW = result.frameW, frameH = result.frameH,
                            lastSeen = now,
                        )
                        return@execute
                    }
                    // Only overlay codes that are actually Homebox labels.
                    val target = parseHomeboxLabel(code) ?: return@execute
                    val cached = resolved[code]
                    detections[code] = ArDetection(
                        code = code,
                        nx = result.nx, ny = result.ny,
                        frameW = result.frameW, frameH = result.frameH,
                        lastSeen = now,
                        resolution = cached ?: Resolution.Loading,
                    )
                    if (cached == null) {
                        resolved[code] = Resolution.Loading
                        scope.launch {
                            val r = resolve(target)
                            resolved[code] = r
                            detections[code]?.let { detections[code] = it.copy(resolution = r) }
                        }
                    }
                }
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

    // Prune cards whose code hasn't been decoded recently, so they fade as the camera pans away.
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            if (detections.isEmpty()) continue
            val now = System.currentTimeMillis()
            detections.filterValues { now - it.lastSeen > DETECTION_TTL_MS }.keys
                .forEach { detections.remove(it) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it },
    ) {
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

        // Classic mode shows an aim reticle; AR mode shows the floating cards instead.
        if (!arMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
            )
        } else if (viewSize != IntSize.Zero) {
            detections.values.forEach { detection ->
                OverlayCard(detection = detection, viewSize = viewSize, onPick = onResult)
            }
        }

        Text(
            text = if (arMode) "Point at a Homebox label" else prompt,
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

        // Bottom-centre controls: the AR toggle sits above the torch button.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (allowAr) {
                FilledIconToggleButton(
                    checked = arMode,
                    onCheckedChange = { on ->
                        arMode = on
                        // Dropping out of AR clears any lingering cards so the classic view is clean.
                        if (!on) detections.clear()
                    },
                ) {
                    Icon(
                        Icons.Filled.ViewInAr,
                        contentDescription = if (arMode) "Turn AR overlay off" else "Turn AR overlay on",
                    )
                }
            }
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                FilledIconButton(
                    onClick = {
                        torchOn = !torchOn
                        runCatching { camera?.cameraControl?.enableTorch(torchOn) }
                    },
                ) {
                    Icon(
                        if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                    )
                }
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
