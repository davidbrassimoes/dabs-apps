package pt.dabs.fueltracker.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.dabs.fueltracker.core.data.FillUpRepository
import pt.dabs.fueltracker.core.domain.ConsumptionCalculator
import pt.dabs.fueltracker.core.domain.model.ConsumptionCycle
import pt.dabs.fueltracker.core.domain.model.FillUp
import javax.inject.Inject

data class HistoryItem(
    val fillUp: FillUp,
    /** Ciclo fechado por este abastecimento; null para o primeiro ou para parciais. */
    val cycle: ConsumptionCycle?,
    val isAnomalous: Boolean,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    /** Do mais recente para o mais antigo. */
    val items: List<HistoryItem> = emptyList(),
)

sealed interface HistoryEvent {
    /** Um abastecimento foi apagado e pode ser reposto com [HistoryViewModel.restore]. */
    data class Deleted(val fillUp: FillUp) : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: FillUpRepository,
) : ViewModel() {

    private val events = Channel<HistoryEvent>(Channel.BUFFERED)
    val eventFlow: Flow<HistoryEvent> = events.receiveAsFlow()

    val uiState: StateFlow<HistoryUiState> = repository.observeAll()
        .map { fillUps -> HistoryUiState(isLoading = false, items = buildItems(fillUps)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun delete(fillUp: FillUp) {
        viewModelScope.launch {
            repository.delete(fillUp)
            events.send(HistoryEvent.Deleted(fillUp))
        }
    }

    fun restore(fillUp: FillUp) {
        viewModelScope.launch { repository.insert(fillUp) }
    }

    private fun buildItems(fillUps: List<FillUp>): List<HistoryItem> {
        val sorted = ConsumptionCalculator.sortChronologically(fillUps)
        val cycles = ConsumptionCalculator.computeCycles(sorted)
        val cyclesById = cycles.associateBy { it.fillUpId }
        val anomalies = ConsumptionCalculator.detectAnomalies(cycles)
        return sorted.asReversed().map { fillUp ->
            HistoryItem(
                fillUp = fillUp,
                cycle = cyclesById[fillUp.id],
                isAnomalous = fillUp.id in anomalies,
            )
        }
    }
}
