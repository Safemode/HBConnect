package com.safemode.hbconnect.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.GroupStatistics
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState

private data class StatTile(val label: String, val value: String, val icon: ImageVector, val route: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenItems: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenServer: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.reloadSilently()
        onPauseOrDispose { }
    }

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = "Not connected yet",
                    message = "Open the menu and go to Server to add your Homebox address and sign in.",
                    actionLabel = "Open Server",
                    onAction = onOpenServer,
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> StatsGrid(
                    stats = s.data,
                    onOpenItems = onOpenItems,
                    onOpenLocations = onOpenLocations,
                    onOpenTags = onOpenTags,
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(
    stats: GroupStatistics,
    onOpenItems: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenTags: () -> Unit,
) {
    val tiles = listOf(
        StatTile("Items", (stats.totalItems ?: 0).toString(), Icons.Filled.Inventory2, "items"),
        StatTile("Locations", (stats.totalLocations ?: 0).toString(), Icons.Outlined.Place, "locations"),
        StatTile("Tags", (stats.totalTags ?: 0).toString(), Icons.Filled.Sell, "tags"),
        StatTile("With warranty", (stats.totalWithWarranty ?: 0).toString(), Icons.Filled.VerifiedUser, null),
        StatTile("Total value", formatCurrency(stats.totalItemPrice), Icons.Filled.AttachMoney, null),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tiles) { tile ->
            val onClick: (() -> Unit)? = when (tile.route) {
                "items" -> onOpenItems
                "locations" -> onOpenLocations
                "tags" -> onOpenTags
                else -> null
            }
            StatCard(tile, onClick)
        }
    }
}

@Composable
private fun StatCard(tile: StatTile, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    tile.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    tile.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                tile.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatCurrency(value: Double?): String {
    val v = value ?: 0.0
    return if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v)
}
