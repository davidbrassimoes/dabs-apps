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
    /** Distância habitual por depósito (média dos últimos ciclos cheio-a-cheio). */
    val typicalDistancePerTankKm: Int? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    fillUpRepository: FillUpRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        fillUpRepository.observeAll()
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
            // O utilizador atesta logo após a reserva: a autonomia útil é o que costuma fazer por depósito.
            typicalDistancePerTankKm = ConsumptionCalculator.typicalDistancePerTankKm(cycles),
        )
    }
}
