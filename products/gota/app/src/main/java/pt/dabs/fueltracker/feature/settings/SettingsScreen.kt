package pt.dabs.fueltracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.csv.FillUpCsv

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel, resources) {
        viewModel.transferEventFlow.collect { event ->
            val message = when (event) {
                is TransferEvent.Exported -> resources.getQuantityString(R.plurals.settings_exported, event.count, event.count)
                is TransferEvent.Imported -> resources.getString(R.string.settings_imported, event.inserted, event.skippedDuplicates, event.invalidRows)
                is TransferEvent.HeaderInvalid -> resources.getString(R.string.settings_import_header_invalid, event.reason)
                TransferEvent.Failed -> resources.getString(R.string.settings_transfer_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FillUpCsv.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportTo(uri.toString())
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri.toString())
    }

    SettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onExport = { exportLauncher.launch(viewModel.suggestedExportFileName()) },
        onImport = { importLauncher.launch(arrayOf(FillUpCsv.MIME_TYPE, "text/comma-separated-values", "text/plain", "application/octet-stream")) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_data_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_data_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onExport, enabled = !state.isTransferring, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_export))
                }
                OutlinedButton(onClick = onImport, enabled = !state.isTransferring, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_import))
                }
            }
        }
    }
}
