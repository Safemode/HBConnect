package com.safemode.hbconnect.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.ui.navigation.MoreDestination

/** Management entries live at the top; server/settings/about are pinned to the bottom. */
private val bottomItems = setOf(
    MoreDestination.ACCOUNT,
    MoreDestination.SERVER,
    MoreDestination.SETTINGS,
    MoreDestination.ABOUT,
)

/**
 * The "More" tab: a hub for destinations that don't warrant a spot in the bottom bar. Tapping a row
 * pushes that screen (with its own back arrow); the bottom bar hides while it's open.
 */
@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    val activeCollection by Graph.activeGroupName.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            MoreDestination.entries
                .filter { it !in bottomItems }
                .forEach { dest ->
                    MoreRow(
                        dest = dest,
                        onOpen = onOpen,
                        subtitle = if (dest == MoreDestination.COLLECTIONS) activeCollection else null,
                    )
                }
        }

        HorizontalDivider()
        MoreDestination.entries
            .filter { it in bottomItems }
            .forEach { dest -> MoreRow(dest, onOpen) }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MoreRow(dest: MoreDestination, onOpen: (String) -> Unit, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(dest.route) }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            dest.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(24.dp))
        Column {
            Text(
                dest.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
