package com.safemode.hbconnect.ui.currency

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
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
import com.safemode.hbconnect.ui.common.UiState

@Composable
fun CurrencyScreen(viewModel: CurrencyViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeCode by viewModel.activeCode.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val s = state) {
        is UiState.Loading -> Centered { CircularProgressIndicator() }
        is UiState.NotConfigured -> Centered {
            Text("Connect to a server under Server first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is UiState.Error -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
        }
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

@Composable
private fun CurrencyList(
    currencies: List<Currency>,
    activeCode: String?,
    busy: Boolean,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, currencies) {
        if (query.isBlank()) currencies
        else currencies.filter {
            it.code?.contains(query, ignoreCase = true) == true ||
                it.name?.contains(query, ignoreCase = true) == true
        }
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
        LazyColumn(modifier = Modifier.weight(1f).navigationBarsPadding()) {
            items(filtered, key = { it.code ?: it.name ?: it.hashCode().toString() }) { currency ->
                val code = currency.code
                CurrencyRow(
                    currency = currency,
                    selected = code != null && code.equals(activeCode, ignoreCase = true),
                    enabled = !busy && code != null,
                    onClick = { if (code != null) onSelect(code) },
                )
            }
        }
    }
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
                "${currency.code ?: "?"} — ${currency.name ?: "Unknown"}",
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

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
