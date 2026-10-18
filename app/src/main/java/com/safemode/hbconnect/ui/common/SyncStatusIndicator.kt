package com.safemode.hbconnect.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.ServerReachability

/**
 * A compact sync-status chip for the dashboard's top bar. It reflects two things at once: whether
 * the server is reachable, and whether the offline outbox has changes still to upload.
 *
 *  - Synced: server reachable and nothing pending (the local copy matches the server).
 *  - Syncing N: server reachable with queued changes still uploading.
 *  - N pending: server unreachable with changes waiting to upload (the local copy is ahead).
 *  - Offline / Checking: server unreachable (or still being probed) with nothing pending - the
 *    local copy is up to date, just not confirmed against a live server.
 */
@Composable
fun SyncStatusIndicator(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val state by Graph.serverStatus.state.collectAsStateWithLifecycle()
    val pending by Graph.uploadQueue.count.collectAsStateWithLifecycle(initialValue = 0)

    val reachable = state == ServerReachability.REACHABLE
    val info: StatusInfo = when {
        reachable && pending == 0 -> StatusInfo(Icons.Filled.CloudDone, "Synced", MaterialTheme.colorScheme.primary)
        reachable -> StatusInfo(Icons.Filled.CloudUpload, "Syncing $pending", MaterialTheme.colorScheme.primary)
        pending > 0 -> StatusInfo(Icons.Filled.CloudOff, "$pending pending", MaterialTheme.colorScheme.error)
        state == ServerReachability.CHECKING ->
            StatusInfo(Icons.Filled.Sync, "Checking", MaterialTheme.colorScheme.onSurfaceVariant)
        else -> StatusInfo(Icons.Filled.CloudOff, "Offline", MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(
        modifier = modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(info.icon, contentDescription = info.label, tint = info.tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(info.label, style = MaterialTheme.typography.labelMedium, color = info.tint)
    }
}

private data class StatusInfo(val icon: ImageVector, val label: String, val tint: Color)
