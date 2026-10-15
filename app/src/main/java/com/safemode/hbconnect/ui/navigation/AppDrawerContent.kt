package com.safemode.hbconnect.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Pinned to the bottom of the drawer, below a divider. */
private val bottomItems = listOf(
    TopDestination.SERVER,
    TopDestination.ABOUT,
    TopDestination.SETTINGS,
)

/**
 * The navigation drawer's contents: a branded header, the primary destinations up top, and the
 * server/about/settings pinned to the bottom below a divider.
 */
@Composable
fun AppDrawerContent(
    currentRoute: String?,
    onDestinationClick: (TopDestination) -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
            Text(
                text = "HBConnect",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Homebox companion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            TopDestination.entries
                .filter { it !in bottomItems }
                .forEach { dest -> DrawerItem(dest, currentRoute, onDestinationClick) }
        }

        HorizontalDivider()
        bottomItems.forEach { dest -> DrawerItem(dest, currentRoute, onDestinationClick) }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerItem(
    dest: TopDestination,
    currentRoute: String?,
    onClick: (TopDestination) -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(dest.label) },
        icon = { Icon(dest.icon, contentDescription = null) },
        selected = dest.route == currentRoute,
        onClick = { onClick(dest) },
        modifier = Modifier.drawerRow(),
    )
}

private val DrawerPillHeight = 48.dp
private val DrawerRowHeight = 56.dp

private fun Modifier.drawerRow(): Modifier =
    padding(NavigationDrawerItemDefaults.ItemPadding)
        .padding(vertical = (DrawerRowHeight - DrawerPillHeight) / 2)
        .height(DrawerPillHeight)
