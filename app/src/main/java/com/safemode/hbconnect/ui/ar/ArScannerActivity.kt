package com.safemode.hbconnect.ui.ar

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
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.ui.scan.BarcodeDecoder
import com.safemode.hbconnect.ui.scan.ScanMode
import com.safemode.hbconnect.ui.scan.ScanTarget
import com.safemode.hbconnect.ui.scan.awaitCameraProvider
import com.safemode.hbconnect.ui.scan.parseHomeboxLabel
import com.safemode.hbconnect.ui.theme.HBConnectTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Overlay-AR scanner: a live camera that recognizes Homebox QR labels and floats a small item card
 * over each code it sees, pinned roughly where the code sits in the frame. Tapping a card opens that
 * item. This is "overlay AR" (info drawn over the camera feed), not spatial ARCore - it reuses the
 * same CameraX + ZXing pipeline as the one-shot [com.safemode.hbconnect.ui.scan.BarcodeScannerActivity].
 *
 * It runs as a standalone full-screen activity (like the one-shot scanner) and returns the chosen
 * entity id to its caller, which then navigates to the detail screen inside the Compose nav graph.
 */
class ArScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HBConnectTheme {
                ArScannerScreen(
                    onPick = { entityId ->
                        setResult(RESULT_OK, Intent().putExtra(ArScanContract.EXTRA_RESULT, entityId))
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

/** Launches the AR scanner and returns the id of the item the user tapped, or null if cancelled. */
class ArScanContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, ArScannerActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_RESULT) else null

    companion object {
        const val EXTRA_RESULT = "ar_scan_result"
    }
}

/** The item details shown on an overlay card once a scanned code has been resolved. */
private data class ArItem(
    val id: String,
    val name: String,
    val subtitle: String?,
    val imageUrl: String?,
)

/** Per-code resolution state: looking it up, resolved to an item, or no matching item. */
private sealed interface Resolution {
    data object Loading : Resolution
    data class Found(val item: ArItem) : Resolution
    data object NotFound : Resolution
}

/**
 * A code currently (or recently) in view. [nx]/[ny] are its centre as a fraction of the upright
 * frame, [frameW]/[frameH] that frame's size (for the FILL_CENTER mapping to screen pixels), and
 * [lastSeen] the time it was last decoded so stale cards can be pruned as the camera pans away.
 */
private data class ArDetection(
    val code: String,
    val nx: Float,
    val ny: Float,
    val frameW: Int,
    val frameH: Int,
    val lastSeen: Long,
    val resolution: Resolution,
)

/** How long a card lingers after its code was last decoded, so a brief miss doesn't make it flicker. */
private const val DETECTION_TTL_MS = 1500L

@Composable
private fun ArScannerScreen(onPick: (String) -> Unit, onCancel: () -> Unit) {
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

    // Codes seen within the TTL, keyed by their decoded text (stable per physical label). Updated on
    // the main thread only: the analyzer posts each decode through the main executor below.
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
        // Homebox labels are QR codes, so scan QR only - it is rotation-invariant and the fastest
        // to decode, which keeps the overlay responsive while the camera moves.
        val decoder = BarcodeDecoder(ScanMode.QR.formats)
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            val result = try {
                decoder.decode(proxy)
            } catch (_: Throwable) {
                null
            } finally {
                proxy.close()
            }
            if (result != null) {
                // Hop to the main thread to touch Compose state and start any lookup.
                ContextCompat.getMainExecutor(context).execute {
                    val now = System.currentTimeMillis()
                    val code = result.text
                    val existing = detections[code]
                    if (existing != null) {
                        // Same code still in view: just refresh its position and keep-alive time.
                        detections[code] = existing.copy(
                            nx = result.nx, ny = result.ny,
                            frameW = result.frameW, frameH = result.frameH,
                            lastSeen = now,
                        )
                        return@execute
                    }
                    // A code we only want to show if it is actually a Homebox label.
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
                            // Apply the result only if the card is still showing.
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
            val now = System.currentTimeMillis()
            val stale = detections.filterValues { now - it.lastSeen > DETECTION_TTL_MS }.keys
            stale.forEach { detections.remove(it) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it },
    ) {
        // Tapping empty camera focuses there (tap on a card is handled by the card itself).
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ) {
                    val cam = camera ?: return@clickable
                    val point = previewView.meteringPointFactory.createPoint(
                        viewSize.width / 2f, viewSize.height / 2f,
                    )
                    runCatching {
                        cam.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(point).build(),
                        )
                    }
                },
        )

        // Draw a card for each live detection, pinned over its code.
        if (viewSize != IntSize.Zero) {
            detections.values.forEach { detection ->
                OverlayCard(
                    detection = detection,
                    viewSize = viewSize,
                    onPick = onPick,
                )
            }
        }

        Text(
            text = "Point at a Homebox label",
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

/**
 * One floating card, offset so it sits just above the code it describes. The code's normalized centre
 * is mapped to screen pixels using the same FILL_CENTER crop the [PreviewView] applies, then clamped
 * so the card stays fully on screen. A [Resolution.Found] card is tappable and opens the item.
 */
@Composable
private fun OverlayCard(
    detection: ArDetection,
    viewSize: IntSize,
    onPick: (String) -> Unit,
) {
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    // FILL_CENTER scales the frame to cover the view and centre-crops the overflow.
    val scale = max(
        viewSize.width.toFloat() / detection.frameW,
        viewSize.height.toFloat() / detection.frameH,
    )
    val displayedW = detection.frameW * scale
    val displayedH = detection.frameH * scale
    val offX = (viewSize.width - displayedW) / 2f
    val offY = (viewSize.height - displayedH) / 2f
    val codeX = detection.nx * displayedW + offX
    val codeY = detection.ny * displayedH + offY

    val pad = 16
    val gap = 24 // lift the card above the code so the code itself stays visible
    // Centre the card horizontally on the code; place it above, clamped on screen.
    val rawX = codeX - cardSize.width / 2f
    val rawY = codeY - cardSize.height - gap
    val x = rawX.coerceIn(pad.toFloat(), max(pad.toFloat(), viewSize.width - cardSize.width - pad.toFloat()))
    val y = rawY.coerceIn(pad.toFloat(), max(pad.toFloat(), viewSize.height - cardSize.height - pad.toFloat()))

    Box(
        modifier = Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .onSizeChanged { cardSize = it }
            .widthIn(max = 260.dp),
    ) {
        when (val r = detection.resolution) {
            Resolution.Loading -> InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Looking up…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Resolution.NotFound -> InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "No matching item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is Resolution.Found -> FoundCard(item = r.item, onPick = onPick)
        }
    }
}

/** A tappable card showing the resolved item's photo, name and location/quantity. */
@Composable
private fun FoundCard(item: ArItem, onPick: (String) -> Unit) {
    InfoCard(onClick = { onPick(item.id) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp),
                    )
                } else {
                    Icon(
                        Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shared card chrome for the overlay: a slightly raised surface with consistent padding. */
@Composable
private fun InfoCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { content() }
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
                "Camera access is needed to scan labels.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onCancel) { Text("Close") }
        }
    }
}

/** Resolves a parsed Homebox label to the item card, or [Resolution.NotFound] if nothing matches. */
private suspend fun resolve(target: ScanTarget): Resolution = when (target) {
    is ScanTarget.Entity ->
        Graph.repository.getEntity(target.id).fold(
            onSuccess = { Resolution.Found(it.toArItem()) },
            onFailure = { Resolution.NotFound },
        )
    is ScanTarget.Asset ->
        Graph.repository.findByAssetId(target.assetId).fold(
            onSuccess = { items -> items.firstOrNull()?.let { Resolution.Found(it.toArItem()) } ?: Resolution.NotFound },
            onFailure = { Resolution.NotFound },
        )
}

/** Builds the overlay subtitle from an item's location and (non-trivial) quantity. */
private fun subtitleOf(locationName: String?, quantity: Int?): String? {
    val parts = buildList {
        quantity?.takeIf { it != 1 }?.let { add("Qty $it") }
        locationName?.let { add(it) }
    }
    return parts.joinToString(" · ").ifBlank { null }
}

private fun EntityOut.toArItem(): ArItem = ArItem(
    id = id.orEmpty(),
    name = displayName,
    subtitle = subtitleOf(parent?.displayName, quantity?.toInt().takeIf { isLocation.not() }),
    imageUrl = imageUrlFor(id, thumbnailId ?: imageId, isLocation),
)

private fun EntitySummary.toArItem(): ArItem = ArItem(
    id = id.orEmpty(),
    name = displayName,
    subtitle = subtitleOf(parent?.displayName, quantity?.toInt().takeIf { isLocation.not() }),
    imageUrl = imageUrlFor(id, thumbnailId ?: imageId, isLocation),
)

/** Authenticated thumbnail URL for an item, or null for locations / items without a photo. */
private fun imageUrlFor(id: String?, attachmentId: String?, isLocation: Boolean): String? {
    if (isLocation) return null
    val entityId = id?.ifBlank { null } ?: return null
    val att = attachmentId?.ifBlank { null } ?: return null
    return Graph.repository.attachmentUrl(entityId, att)
}
