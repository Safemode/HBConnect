package com.safemode.hbconnect.ui.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntitySummary

/** Builds the authenticated image URL for an entity summary's thumbnail/image, if any. */
fun entityImageUrl(entity: EntitySummary): String? {
    val id = entity.id ?: return null
    val attachmentId = entity.thumbnailId?.ifBlank { null } ?: entity.imageId?.ifBlank { null } ?: return null
    return Graph.repository.attachmentUrl(id, attachmentId)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntityRow(
    entity: EntitySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val imageUrl = entityImageUrl(entity)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    selected -> Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                    imageUrl != null && !entity.isLocation -> AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp),
                    )
                    else -> Icon(
                        if (entity.isLocation) Icons.Outlined.Place else Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val sub = buildList {
                    if (entity.isLocation) {
                        val count = entity.itemCount?.toInt() ?: 0
                        add("$count item${if (count == 1) "" else "s"}")
                    } else {
                        entity.quantity?.toInt()?.takeIf { it != 1 }?.let { add("Qty $it") }
                        entity.parent?.displayName?.let { add(it) }
                    }
                    entity.tags?.mapNotNull { it.name }?.take(2)?.let { addAll(it) }
                }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
