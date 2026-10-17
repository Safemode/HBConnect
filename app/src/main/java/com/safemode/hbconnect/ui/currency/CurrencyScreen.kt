package com.safemode.hbconnect.ui.currency

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState

/**
 * Currency picker: choose the group's currency (common ones surfaced on top, then the full list);
 * prices format against it throughout the app. Backed by [CurrencyViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyScreen(viewModel: CurrencyViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeCode by viewModel.activeCode.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.AttachMoney,
                    title = "Not connected yet",
                    message = "Go to More → Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> CurrencyList(
                    currencies = s.data,
                    activeCode = activeCode,
                    busy = busy,
                    onSelect = { code ->
                        viewModel.select(code) { ok, err ->
                            if (!ok) Toast.makeText(context, err ?: "Couldn't change currency.", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

// Shown first, in this order, as a shortcut above the full alphabetical list.
private val COMMON_CODES = listOf(
    "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "NZD",
)

@Composable
private fun CurrencyList(
    currencies: List<Currency>,
    activeCode: String?,
    busy: Boolean,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()

    val filtered = remember(query, currencies) {
        currencies.filter {
            it.code?.contains(query, ignoreCase = true) == true ||
                it.name?.contains(query, ignoreCase = true) == true
        }
    }
    val common = remember(currencies) {
        COMMON_CODES.mapNotNull { code -> currencies.firstOrNull { it.code.equals(code, ignoreCase = true) } }
    }
    val selected = remember(currencies, activeCode) {
        currencies.firstOrNull { it.code != null && it.code.equals(activeCode, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search currencies") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (searching) {
                items(filtered, key = { "f_${it.code ?: it.name}" }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        selected = currency.code.equals(activeCode, ignoreCase = true),
                        enabled = !busy && currency.code != null,
                        onClick = { currency.code?.let(onSelect) },
                    )
                }
            } else {
                item(key = "current") { SelectedBanner(selected) }
                if (common.isNotEmpty()) {
                    item(key = "common_label") { SectionLabel("Common") }
                    items(common, key = { "c_${it.code}" }) { currency ->
                        CurrencyRow(
                            currency = currency,
                            selected = currency.code.equals(activeCode, ignoreCase = true),
                            enabled = !busy && currency.code != null,
                            onClick = { currency.code?.let(onSelect) },
                        )
                    }
                }
                item(key = "all_label") { SectionLabel("All currencies") }
                items(currencies, key = { "a_${it.code ?: it.name}" }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        selected = currency.code.equals(activeCode, ignoreCase = true),
                        enabled = !busy && currency.code != null,
                        onClick = { currency.code?.let(onSelect) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedBanner(currency: Currency?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Current",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (currency == null) "-"
                else "${currency.code ?: "?"} - ${currency.name ?: "Unknown"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        currency?.symbol?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun CurrencyRow(
    currency: Currency,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                "${currency.code ?: "?"} - ${currency.name ?: "Unknown"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        currency.symbol?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
