package com.safemode.hbconnect.ui.value

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.TotalsByOrganizer
import com.safemode.hbconnect.data.remote.models.ValueInsights
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.formatMoney
import com.safemode.hbconnect.ui.dashboard.BreakdownCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueBreakdownScreen(
    onOpenEntity: (String) -> Unit,
    viewModel: ValueBreakdownViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val currency by Graph.currency.collectAsStateWithLifecycle()

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.BarChart,
                    title = "Not connected yet",
                    message = "Go to More → Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> if (s.data.itemCount == 0) {
                    EmptyState(
                        icon = Icons.Filled.BarChart,
                        title = "Nothing to break down",
                        message = "Add some items with purchase prices to see where your value sits.",
                    )
                } else {
                    ValueContent(insights = s.data, currency = currency, onOpenEntity = onOpenEntity)
                }
            }
        }
    }
}

@Composable
private fun ValueContent(
    insights: ValueInsights,
    currency: Currency?,
    onOpenEntity: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item { SummaryCard(insights, currency) }
        if (insights.mostExpensive.isNotEmpty()) {
            item { MostExpensiveCard(insights.mostExpensive, currency, onOpenEntity) }
        }
        item { BreakdownCard("By location", insights.byLocation, currency) }
        item { BreakdownCard("By tag", insights.byTag, currency) }
        item { BreakdownCard("By manufacturer", insights.byManufacturer, currency) }
        item { BreakdownCard("By type", insights.byType, currency) }
        item {
            BreakdownCard(
                title = "Insured vs uninsured",
                data = listOf(
                    TotalsByOrganizer(id = "insured", name = "Insured", total = insights.insuredValue),
                    TotalsByOrganizer(id = "uninsured", name = "Uninsured", total = insights.uninsuredValue),
                ),
                currency = currency,
            )
        }
    }
}

@Composable
private fun SummaryCard(insights: ValueInsights, currency: Currency?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Total value",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMoney(insights.totalValue, currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${insights.itemCount} item${if (insights.itemCount == 1) "" else "s"} · " +
                    "${insights.pricedCount} priced",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MostExpensiveCard(
    items: List<EntitySummary>,
    currency: Currency?,
    onOpenEntity: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "Most expensive",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            items.forEach { item ->
                val id = item.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = id != null) { id?.let(onOpenEntity) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        item.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatMoney(item.purchasePrice ?: 0.0, currency),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
