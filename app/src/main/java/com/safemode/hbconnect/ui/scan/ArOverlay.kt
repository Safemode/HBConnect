package com.safemode.hbconnect.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntitySummary
import kotlin.math.max

/**
 * The overlay-AR half of the scanner: the floating item cards drawn over recognized Homebox labels,
 * and the lookup that turns a scanned code into card contents. Kept separate from [BarcodeScanner]'s
 * camera plumbing so each file stays focused. Used only when the scanner is in AR mode.
 */

/** The item details shown on an overlay card once a scanned code has been resolved. */
internal data class ArItem(
    val id: String,
    val name: String,
    val subtitle: String?,
    val imageUrl: String?,
)

/** Per-code resolution state: looking it up, resolved to an item, or no matching item. */
internal sealed interface Resolution {
    data object Loading : Resolution
    data class Found(val item: ArItem) : Resolution
    data object NotFound : Resolution
}

/**
 * A code currently (or recently) in view. [nx]/[ny] are its centre as a fraction of the upright
 * frame, [frameW]/[frameH] that frame's size (for the FILL_CENTER mapping to screen pixels), and
 * [lastSeen] the time it was last decoded so stale cards can be pruned as the camera pans away.
 */
internal data class ArDetection(
    val code: String,
    val nx: Float,
    val ny: Float,
    val frameW: Int,
    val frameH: Int,
    val lastSeen: Long,
    val resolution: Resolution,
)

/** How long a card lingers after its code was last decoded, so a brief miss doesn't make it flicker. */
internal const val DETECTION_TTL_MS = 1500L

/**
 * One floating card, offset so it sits just above the code it describes. The code's normalized centre
 * is mapped to screen pixels using the same FILL_CENTER crop the preview applies, then clamped so the
 * card stays fully on screen. A [Resolution.Found] card is tappable and opens the item.
 */
@Composable
internal fun OverlayCard(
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

    val pad = 16f
    val gap = 24 // lift the card above the code so the code itself stays visible
    // Centre the card horizontally on the code; place it above, clamped on screen.
    val rawX = codeX - cardSize.width / 2f
    val rawY = codeY - cardSize.height - gap
    val x = rawX.coerceIn(pad, max(pad, viewSize.width - cardSize.width - pad))
    val y = rawY.coerceIn(pad, max(pad, viewSize.height - cardSize.height - pad))

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

/** Resolves a parsed Homebox label to the item card, or [Resolution.NotFound] if nothing matches. */
internal suspend fun resolve(target: ScanTarget): Resolution = when (target) {
    is ScanTarget.Entity ->
        Graph.repository.getEntity(target.id).fold(
            onSuccess = { it.toArItem()?.let(Resolution::Found) ?: Resolution.NotFound },
            onFailure = { Resolution.NotFound },
        )
    is ScanTarget.Asset ->
        Graph.repository.findByAssetId(target.assetId).fold(
            onSuccess = { items -> items.firstOrNull()?.toArItem()?.let(Resolution::Found) ?: Resolution.NotFound },
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

/** Maps a full entity to card contents, or null if it has no usable id. */
private fun EntityOut.toArItem(): ArItem? {
    val entityId = id?.ifBlank { null } ?: return null
    return ArItem(
        id = entityId,
        name = displayName,
        subtitle = subtitleOf(parent?.displayName, quantity?.toInt().takeIf { isLocation.not() }),
        imageUrl = imageUrlFor(entityId, thumbnailId ?: imageId, isLocation),
    )
}

/** Maps an entity summary (from an asset-tag lookup) to card contents, or null if it has no id. */
private fun EntitySummary.toArItem(): ArItem? {
    val entityId = id?.ifBlank { null } ?: return null
    return ArItem(
        id = entityId,
        name = displayName,
        subtitle = subtitleOf(parent?.displayName, quantity?.toInt().takeIf { isLocation.not() }),
        imageUrl = imageUrlFor(entityId, thumbnailId ?: imageId, isLocation),
    )
}

/** Authenticated thumbnail URL for an item, or null for locations / items without a photo. */
private fun imageUrlFor(entityId: String, attachmentId: String?, isLocation: Boolean): String? {
    if (isLocation) return null
    val att = attachmentId?.ifBlank { null } ?: return null
    return Graph.repository.attachmentUrl(entityId, att)
}
