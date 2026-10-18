package com.safemode.hbconnect.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Place
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
import com.safemode.hbconnect.ui.common.formatMoney
import kotlin.math.max

/**
 * The overlay-AR half of the scanner: the floating cards drawn over recognized Homebox labels, and
 * the lookup that turns a scanned code into card contents. Kept separate from [BarcodeScanner]'s
 * camera plumbing so each file stays focused. Used only when the scanner is in AR mode.
 */

/**
 * What an overlay card shows. An [Item] shows a photo, name, location/quantity and price; a
 * [Location] shows its sub-location count and a preview of the items it holds. Both carry the entity
 * [id] the card taps through to.
 */
internal sealed interface ArCard {
    val id: String
    val name: String

    data class Item(
        override val id: String,
        override val name: String,
        val subtitle: String?,
        val priceText: String?,
        val imageUrl: String?,
    ) : ArCard

    data class Location(
        override val id: String,
        override val name: String,
        val subLocationCount: Int,
        val itemCount: Int,
        /** Up to three contained item names, for the "A, B, C, +N more" preview. */
        val sampleItemNames: List<String>,
    ) : ArCard
}

/** Per-code resolution state: looking it up, resolved to a card, or no matching entity. */
internal sealed interface Resolution {
    data object Loading : Resolution
    data class Found(val card: ArCard) : Resolution
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
 * card stays fully on screen. A [Resolution.Found] card is tappable and opens the entity.
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
                        "No matching entity",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is Resolution.Found -> when (val card = r.card) {
                is ArCard.Item -> ItemCard(card, onPick)
                is ArCard.Location -> LocationCard(card, onPick)
            }
        }
    }
}

/** A tappable card showing a scanned item's photo, name, location/quantity and price. */
@Composable
private fun ItemCard(item: ArCard.Item, onPick: (String) -> Unit) {
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
                item.priceText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
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

/**
 * A tappable card for a scanned location: its name, sub-location count, and a preview of the items it
 * holds (the first few names, then "+N more" when there are more than fit).
 */
@Composable
private fun LocationCard(location: ArCard.Location, onPick: (String) -> Unit) {
    InfoCard(onClick = { onPick(location.id) }) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    location.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                subLocationLine(location.subLocationCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (location.itemCount == 0) {
                Text(
                    "No items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${location.itemCount} item${if (location.itemCount == 1) "" else "s"}:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                location.sampleItemNames.forEach { itemName ->
                    Text(
                        itemName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val more = location.itemCount - location.sampleItemNames.size
                if (more > 0) {
                    Text(
                        "+ $more more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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

/** "No sub-locations" / "1 sub-location" / "N sub-locations". */
private fun subLocationLine(count: Int): String = when (count) {
    0 -> "No sub-locations"
    1 -> "1 sub-location"
    else -> "$count sub-locations"
}

/** Resolves a parsed Homebox label to its card, or [Resolution.NotFound] if nothing matches. */
internal suspend fun resolve(target: ScanTarget): Resolution = when (target) {
    is ScanTarget.Entity -> resolveEntity(target.id)
    is ScanTarget.Asset -> resolveAsset(target.assetId)
}

private suspend fun resolveEntity(id: String): Resolution {
    val entity = Graph.repository.getEntity(id).getOrNull() ?: return Resolution.NotFound
    val entityId = entity.id?.ifBlank { null } ?: return Resolution.NotFound
    return if (entity.isLocation) {
        Resolution.Found(locationCard(entityId, entity.displayName))
    } else {
        Resolution.Found(
            ArCard.Item(
                id = entityId,
                name = entity.displayName,
                subtitle = subtitleOf(entity.parent?.displayName, entity.quantity?.toInt()),
                priceText = priceText(entity.purchasePrice),
                imageUrl = imageUrlFor(entityId, entity.thumbnailId ?: entity.imageId, isLocation = false),
            ),
        )
    }
}

private suspend fun resolveAsset(assetId: String): Resolution {
    val summary = Graph.repository.findByAssetId(assetId).getOrNull()?.firstOrNull()
        ?: return Resolution.NotFound
    val entityId = summary.id?.ifBlank { null } ?: return Resolution.NotFound
    // Asset tags are printed on items, but resolve a location defensively just in case.
    return if (summary.isLocation) {
        Resolution.Found(locationCard(entityId, summary.displayName))
    } else {
        Resolution.Found(
            ArCard.Item(
                id = entityId,
                name = summary.displayName,
                subtitle = subtitleOf(summary.parent?.displayName, summary.quantity?.toInt()),
                priceText = priceText(summary.purchasePrice),
                imageUrl = imageUrlFor(entityId, summary.thumbnailId ?: summary.imageId, isLocation = false),
            ),
        )
    }
}

/**
 * Builds a location card. The entities endpoint returns items and sub-locations from separate
 * queries (items by default, locations only when isLocation=true is asked for), so we fetch both:
 * [getChildren] for the items and [getChildLocations] for the sub-locations.
 */
private suspend fun locationCard(id: String, name: String): ArCard.Location {
    val items = Graph.repository.getChildren(id).getOrDefault(emptyList()).filter { !it.isLocation }
    val subLocations = Graph.repository.getChildLocations(id).getOrDefault(emptyList())
    return ArCard.Location(
        id = id,
        name = name,
        subLocationCount = subLocations.size,
        itemCount = items.size,
        sampleItemNames = items.take(3).map { it.displayName },
    )
}

/** Formats a positive purchase price with the group's currency; null when there's no price. */
private fun priceText(price: Double?): String? =
    price?.takeIf { it > 0.0 }?.let { formatMoney(it, Graph.currency.value) }

/** Builds the item subtitle from its location and (non-trivial) quantity. */
private fun subtitleOf(locationName: String?, quantity: Int?): String? {
    val parts = buildList {
        quantity?.takeIf { it != 1 }?.let { add("Qty $it") }
        locationName?.let { add(it) }
    }
    return parts.joinToString(" · ").ifBlank { null }
}

/** Authenticated thumbnail URL for an item, or null for locations / items without a photo. */
private fun imageUrlFor(entityId: String, attachmentId: String?, isLocation: Boolean): String? {
    if (isLocation) return null
    val att = attachmentId?.ifBlank { null } ?: return null
    return Graph.repository.attachmentUrl(entityId, att)
}
