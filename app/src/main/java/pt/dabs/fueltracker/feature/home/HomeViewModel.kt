package pt.dabs.fueltracker.feature.home

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
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val fillUpCount: Int = 0,
    val lastFillUp: FillUp? = null,
    val lastCycle: ConsumptionCycle? = null,
    val recentAverageMlPer100Km: Int? = null,
    val overallAverageMlPer100Km: Int? = null,
    val thisMonth: PeriodSummary? = null,
    val estimatedRange: ConsumptionCalculator.RangeEstimate? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: FillUpRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeAll()
        .map { fillUps -> buildState(fillUps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildState(fillUps: List<FillUp>): HomeUiState {
        val zone = clock.zone
        val sorted = ConsumptionCalculator.sortChronologically(fillUps)
        val cycles = ConsumptionCalculator.computeCycles(sorted)
        return HomeUiState(
            isLoading = false,
            fillUpCount = sorted.size,
            lastFillUp = sorted.lastOrNull(),
            lastCycle = cycles.lastOrNull(),
            recentAverageMlPer100Km = ConsumptionCalculator.recentAverageMlPer100Km(cycles),
            overallAverageMlPer100Km = ConsumptionCalculator.weightedAverageMlPer100Km(cycles),
            thisMonth = ConsumptionCalculator.monthSummary(sorted, LocalDate.now(clock), zone),
            // Capacidade do depósito chega nas Definições (fase posterior); até lá usa o fallback.
            estimatedRange = ConsumptionCalculator.estimateRangeKm(tankCapacityMl = null, cycles = cycles),
        )
    }
}
