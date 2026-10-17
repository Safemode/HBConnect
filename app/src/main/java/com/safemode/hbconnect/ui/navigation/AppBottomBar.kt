package com.safemode.hbconnect.ui.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Which tab reads as selected for a given route. Home/Items/Locations map to themselves; every
 * screen reached from the More hub (including its own sub-screens) keeps More lit. Entity detail
 * and the add/edit forms belong to no tab, so nothing is highlighted there.
 */
fun selectedTabFor(route: String?): BottomTab? = when (route) {
    Routes.DASHBOARD -> BottomTab.HOME
    Routes.ITEMS -> BottomTab.ITEMS
    Routes.LOCATIONS -> BottomTab.LOCATIONS
    Routes.MORE,
    Routes.TAGS,
    Routes.ENTITY_TYPES,
    Routes.TEMPLATES,
    Routes.MAINTENANCE_ALL,
    Routes.VALUE_BREAKDOWN,
    Routes.IMPORT_EXPORT,
    Routes.COLLECTIONS,
    Routes.MEMBERS,
    Routes.CURRENCY,
    Routes.SERVER,
    Routes.SETTINGS,
    Routes.ABOUT,
    -> BottomTab.MORE
    else -> null
}

/**
 * The app's primary navigation: Home, Items, a center Add action, Locations, and More. It stays
 * visible on every screen. Add isn't a tab — it opens the new-item form — so it never shows a
 * selected state.
 */
@Composable
fun AppBottomBar(
    selectedTab: BottomTab?,
    onSelectTab: (BottomTab) -> Unit,
    onAdd: () -> Unit,
) {
    NavigationBar {
        TabItem(BottomTab.HOME, selectedTab, onSelectTab)
        TabItem(BottomTab.ITEMS, selectedTab, onSelectTab)
        NavigationBarItem(
            selected = false,
            onClick = onAdd,
            icon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
            label = { Text("Add") },
        )
        TabItem(BottomTab.LOCATIONS, selectedTab, onSelectTab)
        TabItem(BottomTab.MORE, selectedTab, onSelectTab)
    }
}

@Composable
private fun RowScope.TabItem(
    tab: BottomTab,
    selectedTab: BottomTab?,
    onSelectTab: (BottomTab) -> Unit,
) {
    NavigationBarItem(
        selected = tab == selectedTab,
        onClick = { onSelectTab(tab) },
        icon = { Icon(tab.icon, contentDescription = null) },
        label = { Text(tab.label) },
    )
}
