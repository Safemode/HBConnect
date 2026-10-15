package com.safemode.hbconnect.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.ServerReachability
import com.safemode.hbconnect.ui.about.AboutScreen
import com.safemode.hbconnect.ui.dashboard.DashboardScreen
import com.safemode.hbconnect.ui.entity.EntityDetailScreen
import com.safemode.hbconnect.ui.items.ItemFormScreen
import com.safemode.hbconnect.ui.items.ItemsScreen
import com.safemode.hbconnect.ui.locations.LocationsScreen
import com.safemode.hbconnect.ui.navigation.AppDrawerContent
import com.safemode.hbconnect.ui.navigation.Routes
import com.safemode.hbconnect.ui.navigation.TopDestination
import com.safemode.hbconnect.ui.server.ServerScreen
import com.safemode.hbconnect.ui.settings.SettingsScreen
import com.safemode.hbconnect.ui.tags.TagsScreen
import kotlinx.coroutines.launch

@Composable
fun HBConnectRoot() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val serverState by Graph.serverStatus.state.collectAsStateWithLifecycle()
    val showBanner = serverState != ServerReachability.REACHABLE

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = currentRoute == Routes.DASHBOARD && !drawerState.isOpen) {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000L) {
            context.findActivity()?.finish()
        } else {
            lastBackPress = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentRoute = currentRoute,
                onDestinationClick = { dest ->
                    scope.launch { drawerState.close() }
                    if (currentRoute != dest.route) {
                        navController.navigate(dest.route) {
                            popUpTo(Routes.DASHBOARD)
                            launchSingleTop = true
                        }
                    }
                },
            )
        },
    ) {
        val contentModifier = if (showBanner) {
            Modifier.fillMaxSize().statusBarsPadding()
        } else {
            Modifier.fillMaxSize()
        }
        Column(modifier = contentModifier) {
            ConnectivityBanner(state = serverState)
            val topDest = TopDestination.entries.firstOrNull { it.route == currentRoute }
            if (topDest != null) {
                TopBar(
                    title = if (topDest == TopDestination.DASHBOARD) "HBConnect" else topDest.label,
                    onOpenDrawer = openDrawer,
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.weight(1f),
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onOpenItems = { navController.navigate(Routes.ITEMS) },
                        onOpenLocations = { navController.navigate(Routes.LOCATIONS) },
                        onOpenTags = { navController.navigate(Routes.TAGS) },
                        onOpenServer = { navController.navigate(Routes.SERVER) },
                    )
                }
                composable(Routes.ITEMS) {
                    ItemsScreen(
                        onOpenEntity = { id -> navController.navigate(Routes.entityDetail(id)) },
                        onAddItem = { navController.navigate(Routes.itemForm()) },
                    )
                }
                composable(Routes.LOCATIONS) {
                    LocationsScreen(
                        onOpenEntity = { id -> navController.navigate(Routes.entityDetail(id)) },
                    )
                }
                composable(Routes.TAGS) { TagsScreen() }
                composable(
                    route = Routes.ENTITY_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    EntityDetailScreen(
                        entityId = id,
                        onBack = { navController.popBackStack() },
                        onEditItem = { itemId -> navController.navigate(Routes.itemForm(itemId = itemId)) },
                        onOpenEntity = { childId -> navController.navigate(Routes.entityDetail(childId)) },
                        onAddItemHere = { parentId -> navController.navigate(Routes.itemForm(parentId = parentId)) },
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
                composable(Routes.SERVER) { ServerScreen() }
                composable(Routes.ABOUT) { AboutScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
            }
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(title: String, onOpenDrawer: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
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
