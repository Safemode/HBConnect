package com.safemode.hbconnect.ui.locations

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField
import com.safemode.hbconnect.ui.common.UiState

@Suppress("UNCHECKED_CAST")
private fun locationFormFactory(locationId: String?, parentId: String?) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LocationFormViewModel(locationId, parentId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationFormScreen(
    locationId: String?,
    parentId: String?,
    onBack: () -> Unit,
    viewModel: LocationFormViewModel = viewModel(
        key = "locationForm:${locationId.orEmpty()}:${parentId.orEmpty()}",
        factory = locationFormFactory(locationId, parentId),
    ),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val loadState by viewModel.load.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "Edit location" else "New location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !saving && loadState is UiState.Success,
                        onClick = {
                            viewModel.save { ok, err ->
                                if (ok) {
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, err ?: "Save failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    ) {
                        if (saving) CircularProgressIndicator(modifier = Modifier.padding(4.dp)) else Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = loadState) {
            is UiState.Error -> ErrorState(message = s.message, modifier = Modifier.padding(padding))
            is UiState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OptionDropdownField(
                    label = "Parent location",
                    options = listOf(Option("(None)", null)) + parents.map { Option(it.displayName, it.id) },
                    selectedValue = form.parentId,
                    onSelect = { opt -> viewModel.update { it.copy(parentId = opt.value) } },
                )
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        }
    }
}
