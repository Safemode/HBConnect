package com.safemode.hbconnect.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DASHBOARD = "dashboard"
    const val ITEMS = "items"
    const val LOCATIONS = "locations"
    const val TAGS = "tags"
    const val MAINTENANCE_ALL = "maintenance"
    const val SERVER = "server"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    /** Detail for any entity (item or location). */
    fun entityDetail(id: String) = "entity/$id"
    const val ENTITY_DETAIL = "entity/{id}"

    /** Item add/edit form. Pass [itemId] to edit; pass [parentId] to preselect a location. */
    fun itemForm(itemId: String? = null, parentId: String? = null) =
        "itemForm?itemId=${itemId ?: ""}&parentId=${parentId ?: ""}"
    const val ITEM_FORM = "itemForm?itemId={itemId}&parentId={parentId}"

    /** Location add/edit form. Pass [locationId] to edit; pass [parentId] to preselect a parent. */
    fun locationForm(locationId: String? = null, parentId: String? = null) =
        "locationForm?locationId=${locationId ?: ""}&parentId=${parentId ?: ""}"
    const val LOCATION_FORM = "locationForm?locationId={locationId}&parentId={parentId}"

    /** Maintenance log for an item. */
    fun maintenance(entityId: String) = "maintenance/$entityId"
    const val MAINTENANCE = "maintenance/{entityId}"

    /** Attachment manager for an item. */
    fun attachments(entityId: String) = "attachments/$entityId"
    const val ATTACHMENTS = "attachments/{entityId}"
}

/** Top-level destinations shown in the navigation drawer. */
enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD(Routes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
    ITEMS(Routes.ITEMS, "Items", Icons.Filled.Inventory2),
    LOCATIONS(Routes.LOCATIONS, "Locations", Icons.Outlined.Place),
    TAGS(Routes.TAGS, "Tags", Icons.Filled.Sell),
    MAINTENANCE(Routes.MAINTENANCE_ALL, "Maintenance", Icons.Filled.Build),
    SERVER(Routes.SERVER, "Server", Icons.Filled.Dns),
    ABOUT(Routes.ABOUT, "About", Icons.Filled.Info),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}
