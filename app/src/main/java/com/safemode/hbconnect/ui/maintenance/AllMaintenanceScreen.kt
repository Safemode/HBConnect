package com.safemode.hbconnect.ui.maintenance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState

/**
 * Group-wide maintenance view: scheduled and completed upkeep across every item, each with its
 * name, description, cost and dates. Backed by [AllMaintenanceViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllMaintenanceScreen(
    onOpenEntity: (String) -> Unit,
    viewModel: AllMaintenanceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load(showLoading = false)
        onPauseOrDispose { }
    }

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MaintenanceScope.entries.forEach { s ->
                    FilterChip(
                        selected = scope == s,
                        onClick = { viewModel.setScope(s) },
                        label = { Text(s.label) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                when (val s = state) {
                    UiState.Loading -> LoadingState()
                    UiState.NotConfigured -> EmptyState(
                        icon = Icons.Filled.Build,
                        title = "Not connected yet",
                        message = "Go to More → Server to connect to your Homebox.",
                    )
                    is UiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load(showLoading = true) })
                    is UiState.Success -> if (s.data.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Build,
                            title = if (scope == MaintenanceScope.COMPLETED) "Nothing completed" else "Nothing scheduled",
                            message = "Maintenance you log on items shows up here.",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(s.data, key = { it.id ?: it.hashCode().toString() }) { entry ->
                                MaintenanceSummaryRow(
                                    entry = entry,
                                    onClick = { entry.itemID?.let(onOpenEntity) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceSummaryRow(entry: MaintenanceEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                entry.name ?: "(entry)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            entry.itemName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            val meta = buildList {
                if (entry.isCompleted) {
                    entry.completedDate?.substringBefore('T')?.let { add("Completed $it") }
                } else {
                    entry.scheduledDate?.substringBefore('T')
                        ?.takeIf { it.isNotBlank() && it != "0001-01-01" }
                        ?.let { add("Scheduled $it") }
                }
                entry.cost?.takeIf { it.isNotBlank() && it != "0" }?.let { add("Cost $it") }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
