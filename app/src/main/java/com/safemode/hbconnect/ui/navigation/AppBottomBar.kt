package com.safemode.hbconnect.ui.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Routes that keep the bottom bar visible. Everything else (details, forms, More sub-screens) hides it. */
val bottomBarRoutes = setOf(Routes.DASHBOARD, Routes.ITEMS, Routes.LOCATIONS, Routes.MORE)

/**
 * The app's primary navigation: Home, Items, a center Add action, Locations, and More. Add isn't a
 * tab — it opens the new-item form — so it never shows a selected state.
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onSelectTab: (BottomTab) -> Unit,
    onAdd: () -> Unit,
) {
    NavigationBar {
        TabItem(BottomTab.HOME, currentRoute, onSelectTab)
        TabItem(BottomTab.ITEMS, currentRoute, onSelectTab)
        NavigationBarItem(
            selected = false,
            onClick = onAdd,
            icon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
            label = { Text("Add") },
        )
        TabItem(BottomTab.LOCATIONS, currentRoute, onSelectTab)
        TabItem(BottomTab.MORE, currentRoute, onSelectTab)
    }
}

@Composable
private fun RowScope.TabItem(
    tab: BottomTab,
    currentRoute: String?,
    onSelectTab: (BottomTab) -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == tab.route,
        onClick = { onSelectTab(tab) },
        icon = { Icon(tab.icon, contentDescription = null) },
        label = { Text(tab.label) },
    )
}
