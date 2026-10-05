package pt.dabs.fueltracker.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.domain.model.DistanceSource
import pt.dabs.fueltracker.core.domain.model.FillUp
import pt.dabs.fueltracker.core.ui.format.PtFormat
import java.time.ZoneId

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddFillUp: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HistoryEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(event.fillUp)
                }
            }
        }
    }

    HistoryContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = viewModel::delete,
        onAddFillUp = onAddFillUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    state: HistoryUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (FillUp) -> Unit,
    onAddFillUp: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<FillUp?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFillUp) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_fill_up))
            }
        },
    ) { padding ->
        if (!state.isLoading && state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.fillUp.id }) { item ->
                    HistoryRow(
                        item = item,
                        onClick = { onEdit(item.fillUp.id) },
                        onDeleteClick = { pendingDelete = item.fillUp },
                    )
                }
            }
        }
    }

    pendingDelete?.let { fillUp ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_body)) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(fillUp) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val fillUp = item.fillUp
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(PtFormat.date(fillUp.dateTime, zone), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(PtFormat.km(fillUp.odometerKm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!fillUp.isFullTank) {
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.history_partial), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(PtFormat.liters(fillUp.volumeMl.toLong()), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(PtFormat.pricePerLiter(fillUp.pricePerLiterMilliCents), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(PtFormat.euros(fillUp.totalCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                item.cycle?.let { cycle ->
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.isAnomalous) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = stringResource(R.string.history_anomaly),
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = stringResource(
                                R.string.history_cycle,
                                PtFormat.consumption(cycle.consumptionMlPer100Km),
                                PtFormat.km(cycle.distanceKm),
                                PtFormat.costPerKm(cycle.costMilliCentsPerKm),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isAnomalous) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                    }
                    when {
                        cycle.tripMismatch -> R.string.history_trip_mismatch
                        cycle.distanceSource == DistanceSource.TRIP -> R.string.history_trip_distance
                        else -> null
                    }?.let { note ->
                        Text(
                            text = stringResource(note),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cycle.tripMismatch) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!fillUp.stationName.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(fillUp.stationName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
