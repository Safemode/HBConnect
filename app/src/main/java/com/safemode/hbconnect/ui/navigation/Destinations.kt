package com.safemode.hbconnect.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DASHBOARD = "dashboard"
    const val ITEMS = "items"
    const val LOCATIONS = "locations"
    const val MORE = "more"
    const val TAGS = "tags"
    const val ENTITY_TYPES = "entity-types"
    const val TEMPLATES = "templates"
    const val MAINTENANCE_ALL = "maintenance"
    const val VALUE_BREAKDOWN = "value-breakdown"
    const val TOOLS = "tools"
    const val IMPORT_EXPORT = "import-export"
    const val BILL_OF_MATERIALS = "bill-of-materials"
    const val BACKUP = "backup"
    const val LOGS = "logs"
    const val COLLECTIONS = "collections"
    const val MEMBERS = "members"
    const val CURRENCY = "currency"
    const val ACCOUNT = "account"
    const val SERVER = "server"
    const val SETTINGS = "settings"

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

/** The five slots of the bottom navigation bar. [ADD] is an action (opens the item form), not a tab. */
enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    ITEMS(Routes.ITEMS, "Items", Icons.Filled.Inventory2),
    LOCATIONS(Routes.LOCATIONS, "Locations", Icons.Outlined.Place),
    MORE(Routes.MORE, "More", Icons.Filled.MoreHoriz),
}

/**
 * Destinations reached from the "More" hub. The management entries sit up top; server/settings/about
 * are pinned to the bottom of that screen below a divider.
 */
enum class MoreDestination(val route: String, val label: String, val icon: ImageVector) {
    COLLECTIONS(Routes.COLLECTIONS, "Collections", Icons.Filled.Collections),
    CURRENCY(Routes.CURRENCY, "Currency", Icons.Filled.AttachMoney),
    ENTITY_TYPES(Routes.ENTITY_TYPES, "Entity types", Icons.Filled.Category),
    MAINTENANCE(Routes.MAINTENANCE_ALL, "Maintenance", Icons.Filled.Build),
    MEMBERS(Routes.MEMBERS, "Members", Icons.Filled.People),
    TAGS(Routes.TAGS, "Tags", Icons.Filled.Sell),
    TEMPLATES(Routes.TEMPLATES, "Templates", Icons.Filled.Description),
    TOOLS(Routes.TOOLS, "Tools", Icons.Filled.Handyman),
    ACCOUNT(Routes.ACCOUNT, "Account", Icons.Filled.Person),
    SERVER(Routes.SERVER, "Server", Icons.Filled.Dns),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}
