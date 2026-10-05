package pt.dabs.fueltracker.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pt.dabs.fueltracker.core.data.FillUpRepository
import pt.dabs.fueltracker.core.domain.ConsumptionCalculator
import pt.dabs.fueltracker.core.domain.model.ConsumptionCycle
import pt.dabs.fueltracker.core.domain.model.FillUp
import pt.dabs.fueltracker.core.domain.model.PeriodSummary
import pt.dabs.fueltracker.core.domain.model.BrandSummary
import pt.dabs.fueltracker.core.domain.model.FuelSummary
import pt.dabs.fueltracker.core.domain.model.StationSummary
import java.time.Clock
import javax.inject.Inject

data class AnomalyItem(val cycle: ConsumptionCycle, val baselineMlPer100Km: Int?)

data class StatsUiState(
    val isLoading: Boolean = true,
    /** Abastecimentos em ordem cronológica. */
    val fillUps: List<FillUp> = emptyList(),
    /** Ciclos cheio-a-cheio em ordem cronológica. */
    val cycles: List<ConsumptionCycle> = emptyList(),
    /** Últimos meses com dados, por ordem cronológica. */
    val months: List<PeriodSummary> = emptyList(),
    /** Anos com dados, do mais recente para o mais antigo. */
    val years: List<PeriodSummary> = emptyList(),
    val stations: List<StationSummary> = emptyList(),
    val brands: List<BrandSummary> = emptyList(),
    val fuels: List<FuelSummary> = emptyList(),
    /** Ciclos com consumo acima do habitual, do mais recente para o mais antigo. */
    val anomalies: List<AnomalyItem> = emptyList(),
    val overallAverageMlPer100Km: Int? = null,
) {
    val hasEnoughData: Boolean get() = cycles.isNotEmpty()
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    repository: FillUpRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = repository.observeAll()
        .map { fillUps -> buildState(fillUps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun buildState(fillUps: List<FillUp>): StatsUiState {
        val sorted = ConsumptionCalculator.sortChronologically(fillUps)
        val cycles = ConsumptionCalculator.computeCycles(sorted)
        val anomalyIds = ConsumptionCalculator.detectAnomalies(cycles)
        val anomalies = cycles.withIndex()
            .filter { (_, cycle) -> cycle.fillUpId in anomalyIds }
            .map { (index, cycle) ->
                val baseline = cycles.subList(maxOf(0, index - ConsumptionCalculator.DEFAULT_ANOMALY_WINDOW), index)
                AnomalyItem(cycle, ConsumptionCalculator.weightedAverageMlPer100Km(baseline))
            }
            .asReversed()
        return StatsUiState(
            isLoading = false,
            fillUps = sorted,
            cycles = cycles,
            months = ConsumptionCalculator.monthlySummaries(sorted, clock.zone).takeLast(MONTHS_SHOWN),
            years = ConsumptionCalculator.yearlySummaries(sorted, clock.zone).asReversed(),
            stations = ConsumptionCalculator.stationSummaries(sorted),
            brands = ConsumptionCalculator.brandSummaries(sorted),
            fuels = ConsumptionCalculator.fuelSummaries(sorted),
            anomalies = anomalies,
            overallAverageMlPer100Km = ConsumptionCalculator.weightedAverageMlPer100Km(cycles),
        )
    }

    private companion object {
        const val MONTHS_SHOWN = 12
    }
}
