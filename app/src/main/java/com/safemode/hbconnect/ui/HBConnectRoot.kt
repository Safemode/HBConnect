package com.safemode.hbconnect.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.ServerReachability
import com.safemode.hbconnect.ui.about.AboutScreen
import com.safemode.hbconnect.ui.attachments.AttachmentsScreen
import com.safemode.hbconnect.ui.dashboard.DashboardScreen
import com.safemode.hbconnect.ui.entity.EntityDetailScreen
import com.safemode.hbconnect.ui.entitytypes.EntityTypesScreen
import com.safemode.hbconnect.ui.items.ItemFormScreen
import com.safemode.hbconnect.ui.items.ItemsScreen
import com.safemode.hbconnect.ui.locations.LocationFormScreen
import com.safemode.hbconnect.ui.locations.LocationsScreen
import com.safemode.hbconnect.ui.maintenance.AllMaintenanceScreen
import com.safemode.hbconnect.ui.maintenance.MaintenanceScreen
import com.safemode.hbconnect.ui.more.MoreScreen
import com.safemode.hbconnect.ui.navigation.AppBottomBar
import com.safemode.hbconnect.ui.navigation.BottomTab
import com.safemode.hbconnect.ui.navigation.Routes
import com.safemode.hbconnect.ui.navigation.selectedTabFor
import com.safemode.hbconnect.ui.server.ServerScreen
import com.safemode.hbconnect.ui.settings.SettingsScreen
import com.safemode.hbconnect.ui.tags.TagsScreen
import com.safemode.hbconnect.ui.templates.TemplatesScreen
import com.safemode.hbconnect.ui.value.ValueBreakdownScreen

@Composable
fun HBConnectRoot() {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val serverState by Graph.serverStatus.state.collectAsStateWithLifecycle()
    val showBanner = serverState != ServerReachability.REACHABLE

    // A maintenance-reminder tap asks to open an item; navigate once, then clear the request.
    val pendingEntityId by Graph.pendingEntityId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEntityId) {
        val id = pendingEntityId ?: return@LaunchedEffect
        navController.navigate(Routes.entityDetail(id))
        Graph.consumePendingEntity()
    }

    // Tap a tab to go to its root, clearing anything above Home. No save/restore: the Add form is a
    // plain (non-tab) destination, and saving/restoring it would strand a later tab tap on the form.
    val onSelectTab: (BottomTab) -> Unit = { tab ->
        if (currentRoute != tab.route) {
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }
    // The bar shows on the item form too. If we're already on a form, do nothing — re-navigating
    // would flash (and clobber an in-progress edit). launchSingleTop still guards a rapid double-tap
    // from elsewhere, where currentRoute hasn't updated between the two taps yet.
    val onAddItem: () -> Unit = {
        if (currentRoute != Routes.ITEM_FORM) {
            navController.navigate(Routes.itemForm()) { launchSingleTop = true }
        }
    }

    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = currentRoute == Routes.DASHBOARD) {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000L) {
            context.findActivity()?.finish()
        } else {
            lastBackPress = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    val rootModifier = if (showBanner) {
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    } else {
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    }
    Column(modifier = rootModifier) {
        ConnectivityBanner(state = serverState)
        // The always-visible bottom bar owns the system navigation-bar inset, so consume it here to
        // stop the screens' own Scaffolds from padding for it a second time.
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier
                .weight(1f)
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            composable(Routes.DASHBOARD) {
                MainScaffold("HBConnect") {
                    DashboardScreen(
                        onOpenItems = { navController.navigate(Routes.ITEMS) },
                        onOpenLocations = { navController.navigate(Routes.LOCATIONS) },
                        onOpenTags = { navController.navigate(Routes.TAGS) },
                        onOpenServer = { navController.navigate(Routes.SERVER) },
                        onOpenEntity = { id -> navController.navigate(Routes.entityDetail(id)) },
                    )
                }
            }
            composable(Routes.ITEMS) {
                // ItemsScreen brings its own top bar (with the archived toggle), so it isn't wrapped
                // in MainScaffold like the other tabs.
                ItemsScreen(
                    onOpenEntity = { id -> navController.navigate(Routes.entityDetail(id)) },
                )
            }
            composable(Routes.LOCATIONS) {
                // LocationsScreen owns its top bar (expand/collapse-all), so no MainScaffold wrapper.
                LocationsScreen(
                    onOpenEntity = { id -> navController.navigate(Routes.entityDetail(id)) },
                    onAddLocation = { navController.navigate(Routes.locationForm()) },
                )
            }
            composable(Routes.MORE) {
                MainScaffold("More") {
                    MoreScreen(onOpen = { route -> navController.navigate(route) })
                }
            }
            composable(Routes.TAGS) {
                // TagsScreen owns its top bar (back + expand/collapse-all), so no SubScaffold wrapper.
                TagsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ENTITY_TYPES) {
                SubScaffold("Entity types", onBack = { navController.popBackStack() }) { EntityTypesScreen() }
            }
            composable(Routes.TEMPLATES) {
                SubScaffold("Templates", onBack = { navController.popBackStack() }) {
                    TemplatesScreen(
                        onOpenEntity = { id -> navController.navigate(Routes.entityDetail(id)) },
                    )
                }
            }
            composable(Routes.MAINTENANCE_ALL) {
                SubScaffold("Maintenance", onBack = { navController.popBackStack() }) {
                    AllMaintenanceScreen(
                        onOpenEntity = { id -> navController.navigate(Routes.entityDetail(id)) },
                    )
                }
            }
            composable(Routes.VALUE_BREAKDOWN) {
                SubScaffold("Value breakdown", onBack = { navController.popBackStack() }) {
                    ValueBreakdownScreen(
                        onOpenEntity = { id -> navController.navigate(Routes.entityDetail(id)) },
                    )
                }
            }
            composable(
                route = Routes.ENTITY_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                EntityDetailScreen(
                    entityId = id,
                    onBack = { navController.popBackStack() },
                    onEditItem = { itemId -> navController.navigate(Routes.itemForm(itemId = itemId)) },
                    onEditLocation = { locId -> navController.navigate(Routes.locationForm(locationId = locId)) },
                    onOpenEntity = { childId -> navController.navigate(Routes.entityDetail(childId)) },
                    onAddItemHere = { parentId -> navController.navigate(Routes.itemForm(parentId = parentId)) },
                    onOpenMaintenance = { itemId -> navController.navigate(Routes.maintenance(itemId)) },
                    onOpenAttachments = { itemId -> navController.navigate(Routes.attachments(itemId)) },
                )
            }
            composable(
                route = Routes.LOCATION_FORM,
                arguments = listOf(
                    navArgument("locationId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("parentId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val locationId = entry.arguments?.getString("locationId").orEmpty().ifBlank { null }
                val parentId = entry.arguments?.getString("parentId").orEmpty().ifBlank { null }
                LocationFormScreen(
                    locationId = locationId,
                    parentId = parentId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.MAINTENANCE,
                arguments = listOf(navArgument("entityId") { type = NavType.StringType }),
            ) { entry ->
                MaintenanceScreen(
                    entityId = entry.arguments?.getString("entityId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ATTACHMENTS,
                arguments = listOf(navArgument("entityId") { type = NavType.StringType }),
            ) { entry ->
                AttachmentsScreen(
                    entityId = entry.arguments?.getString("entityId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ITEM_FORM,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("parentId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val itemId = entry.arguments?.getString("itemId").orEmpty().ifBlank { null }
                val parentId = entry.arguments?.getString("parentId").orEmpty().ifBlank { null }
                ItemFormScreen(
                    itemId = itemId,
                    parentId = parentId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SERVER) {
                SubScaffold("Server", onBack = { navController.popBackStack() }) { ServerScreen() }
            }
            composable(Routes.ABOUT) {
                SubScaffold("About", onBack = { navController.popBackStack() }) { AboutScreen() }
            }
            composable(Routes.SETTINGS) {
                SubScaffold("Settings", onBack = { navController.popBackStack() }) { SettingsScreen() }
            }
        }
        AppBottomBar(
            selectedTab = selectedTabFor(currentRoute),
            onSelectTab = onSelectTab,
            onAdd = onAddItem,
        )
    }
}

@Composable
private fun ConnectivityBanner(state: ServerReachability) {
    when (state) {
        ServerReachability.REACHABLE -> return
        ServerReachability.CHECKING -> ConnectivityBar(
            background = MaterialTheme.colorScheme.secondaryContainer,
            foreground = MaterialTheme.colorScheme.onSecondaryContainer,
            text = "Connecting to server… showing cached data",
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        ServerReachability.UNREACHABLE -> ConnectivityBar(
            background = MaterialTheme.colorScheme.error,
            foreground = MaterialTheme.colorScheme.onError,
            text = "Server unreachable — showing cached data if available",
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ConnectivityBar(
    background: Color,
    foreground: Color,
    text: String,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = foreground, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Chrome for a top-level destination (a bottom-bar tab): a plain app bar above the screen. The bars
 * live inside the NavHost so they animate together with their screen during transitions.
 */
@Composable
private fun MainScaffold(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = title)
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/** Chrome for a pushed screen reached from the More hub: an app bar with a back arrow. */
@Composable
private fun SubScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = title, onBack = onBack)
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
