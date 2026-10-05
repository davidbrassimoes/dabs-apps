package pt.dabs.fueltracker.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.domain.model.Period
import pt.dabs.fueltracker.core.ui.components.StatRow
import pt.dabs.fueltracker.core.ui.format.PtFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(state: StatsUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        if (!state.hasEnoughData) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        ProvideFuelChartTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConsumptionChartCard(state)
                MonthlyChartsCard(state)
                PriceChartCard(state)
                YearsCard(state)
                if (state.stations.isNotEmpty()) StationsCard(state)
                if (state.anomalies.isNotEmpty()) AnomaliesCard(state)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", PtFormat.locale)
private val shortMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy", PtFormat.locale)

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun ConsumptionChartCard(state: StatsUiState) {
    val zone = remember { ZoneId.systemDefault() }
    val labels = remember(state.cycles) { state.cycles.map { shortDate.format(it.endDateTime.atZone(zone)) } }
    val values = remember(state.cycles) { state.cycles.map { it.consumptionMlPer100Km / 1000.0 } }
    ChartCard(
        title = stringResource(R.string.stats_consumption_title),
        subtitle = state.overallAverageMlPer100Km?.let { stringResource(R.string.stats_consumption_subtitle, PtFormat.consumption(it)) },
    ) {
        FuelLineChart(labels = labels, values = values, valueSuffix = " L/100")
    }
}

@Composable
private fun MonthlyChartsCard(state: StatsUiState) {
    val labels = remember(state.months) {
        state.months.map { shortMonth.format((it.period as Period.Month).yearMonth) }
    }
    val euros = remember(state.months) { state.months.map { it.totalCents / 100.0 } }
    val km = remember(state.months) { state.months.map { it.distanceKm.toDouble() } }
    ChartCard(title = stringResource(R.string.stats_monthly_spend_title)) {
        FuelColumnChart(labels = labels, values = euros, valueSuffix = " €")
    }
    ChartCard(title = stringResource(R.string.stats_monthly_km_title), subtitle = stringResource(R.string.stats_monthly_km_subtitle)) {
        FuelColumnChart(labels = labels, values = km, valueSuffix = " km", color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun PriceChartCard(state: StatsUiState) {
    val zone = remember { ZoneId.systemDefault() }
    val labels = remember(state.fillUps) { state.fillUps.map { shortDate.format(it.dateTime.atZone(zone)) } }
    val values = remember(state.fillUps) { state.fillUps.map { it.pricePerLiterMilliCents / 100_000.0 } }
    ChartCard(title = stringResource(R.string.stats_price_title)) {
        FuelLineChart(labels = labels, values = values, valueSuffix = " €/L", decimals = 3, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun YearsCard(state: StatsUiState) {
    val dash = stringResource(R.string.no_value)
    ChartCard(title = stringResource(R.string.stats_years_title)) {
        state.years.forEachIndexed { index, year ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text((year.period as Period.Year).year.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            StatRow(stringResource(R.string.label_fill_ups), year.fillUpCount.toString())
            StatRow(stringResource(R.string.label_distance), PtFormat.km(year.distanceKm))
            StatRow(stringResource(R.string.label_volume), PtFormat.liters(year.volumeMl))
            StatRow(stringResource(R.string.label_spent), PtFormat.euros(year.totalCents))
            StatRow(stringResource(R.string.label_average_consumption), year.averageConsumptionMlPer100Km?.let { PtFormat.consumption(it) } ?: dash)
            StatRow(stringResource(R.string.label_cost_per_km), year.costMilliCentsPerKm?.let { PtFormat.costPerKm(it) } ?: dash)
            StatRow(stringResource(R.string.label_average_price), year.averagePricePerLiterMilliCents?.let { PtFormat.pricePerLiter(it) } ?: dash)
        }
    }
}

@Composable
private fun StationsCard(state: StatsUiState) {
    val dash = stringResource(R.string.no_value)
    ChartCard(title = stringResource(R.string.stats_stations_title)) {
        state.stations.forEachIndexed { index, station ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(station.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    pluralStringResource(R.plurals.stats_station_count, station.fillUpCount, station.fillUpCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatRow(stringResource(R.string.label_volume), PtFormat.liters(station.volumeMl))
            StatRow(stringResource(R.string.label_spent), PtFormat.euros(station.totalCents))
            StatRow(stringResource(R.string.label_average_price), station.averagePricePerLiterMilliCents?.let { PtFormat.pricePerLiter(it) } ?: dash)
        }
    }
}

@Composable
private fun AnomaliesCard(state: StatsUiState) {
    val zone = remember { ZoneId.systemDefault() }
    ChartCard(title = stringResource(R.string.stats_anomalies_title), subtitle = stringResource(R.string.stats_anomalies_subtitle)) {
        state.anomalies.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(PtFormat.date(item.cycle.endDateTime, zone), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = item.baselineMlPer100Km?.let {
                        stringResource(R.string.stats_anomaly_values, PtFormat.consumption(item.cycle.consumptionMlPer100Km), PtFormat.consumption(it))
                    } ?: PtFormat.consumption(item.cycle.consumptionMlPer100Km),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
