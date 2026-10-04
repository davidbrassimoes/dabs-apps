package pt.dabs.fueltracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.ui.components.StatCard
import pt.dabs.fueltracker.core.ui.components.StatRow
import pt.dabs.fueltracker.core.ui.format.PtFormat
import java.time.ZoneId

@Composable
fun HomeScreen(
    onAddFillUp: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state = state, onAddFillUp = onAddFillUp, onOpenHistory = onOpenHistory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onAddFillUp: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.List, contentDescription = stringResource(R.string.home_open_history))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFillUp,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_add_fill_up)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isLoading && state.fillUpCount == 0) {
                EmptyHome()
            } else {
                HomeStats(state)
            }
            Spacer(Modifier.height(80.dp)) // espaço para o FAB
        }
    }
}

@Composable
private fun EmptyHome() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HomeStats(state: HomeUiState) {
    val zone = ZoneId.systemDefault()
    val dash = stringResource(R.string.no_value)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.home_last_consumption),
            value = state.lastCycle?.let { PtFormat.consumption(it.consumptionMlPer100Km) } ?: dash,
            supporting = state.lastCycle?.let { PtFormat.km(it.distanceKm) },
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.home_recent_average),
            value = state.recentAverageMlPer100Km?.let { PtFormat.consumption(it) } ?: dash,
            supporting = state.overallAverageMlPer100Km?.let {
                stringResource(R.string.home_overall_average, PtFormat.consumption(it))
            },
        )
    }

    StatCard(
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.home_estimated_range),
        value = state.estimatedRange?.let { PtFormat.km(it.km) } ?: dash,
        supporting = state.estimatedRange?.let {
            if (it.fromTankCapacity) stringResource(R.string.home_range_from_tank)
            else stringResource(R.string.home_range_from_cycles)
        },
    )

    state.thisMonth?.let { month ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_this_month), style = MaterialTheme.typography.titleMedium)
                StatRow(stringResource(R.string.label_distance), PtFormat.km(month.distanceKm))
                StatRow(stringResource(R.string.label_spent), PtFormat.euros(month.totalCents))
                StatRow(stringResource(R.string.label_volume), PtFormat.liters(month.volumeMl))
                StatRow(
                    stringResource(R.string.label_average_consumption),
                    month.averageConsumptionMlPer100Km?.let { PtFormat.consumption(it) } ?: dash,
                )
                StatRow(
                    stringResource(R.string.label_cost_per_km),
                    month.costMilliCentsPerKm?.let { PtFormat.costPerKm(it) } ?: dash,
                )
                StatRow(
                    stringResource(R.string.label_average_price),
                    month.averagePricePerLiterMilliCents?.let { PtFormat.pricePerLiter(it) } ?: dash,
                )
            }
        }
    }

    state.lastFillUp?.let { last ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_last_fill_up), style = MaterialTheme.typography.titleMedium)
                StatRow(stringResource(R.string.label_date), PtFormat.dateTime(last.dateTime, zone))
                StatRow(stringResource(R.string.label_odometer), PtFormat.km(last.odometerKm))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(PtFormat.liters(last.volumeMl.toLong()), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(PtFormat.pricePerLiter(last.pricePerLiterMilliCents), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(PtFormat.euros(last.totalCents), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
