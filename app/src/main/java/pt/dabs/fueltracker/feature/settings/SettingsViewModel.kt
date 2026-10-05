package pt.dabs.fueltracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.dabs.fueltracker.core.csv.FillUpCsv
import pt.dabs.fueltracker.core.data.DocumentStore
import pt.dabs.fueltracker.core.data.FillUpRepository
import pt.dabs.fueltracker.core.data.SettingsRepository
import pt.dabs.fueltracker.core.domain.Nif
import pt.dabs.fueltracker.core.domain.NumberInput
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class SettingsError { INVALID_NIF, INVALID_CAPACITY }

data class SettingsUiState(
    val isLoading: Boolean = true,
    val nifText: String = "",
    val tankCapacityText: String = "",
    val errors: Set<SettingsError> = emptySet(),
    val showErrors: Boolean = false,
    val saved: Boolean = false,
    val isTransferring: Boolean = false,
) {
    val canSave: Boolean get() = !isLoading && errors.isEmpty()
}

/** Resultado de uma exportação ou importação, para mostrar numa Snackbar. */
sealed interface TransferEvent {
    data class Exported(val count: Int) : TransferEvent
    data class Imported(val inserted: Int, val skippedDuplicates: Int, val invalidRows: Int) : TransferEvent
    data class HeaderInvalid(val reason: String) : TransferEvent
    data object Failed : TransferEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val fillUpRepository: FillUpRepository,
    private val documentStore: DocumentStore,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val transferEvents = Channel<TransferEvent>(Channel.BUFFERED)
    val transferEventFlow: Flow<TransferEvent> = transferEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = repository.settings.first()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    nifText = settings.nif.orEmpty(),
                    tankCapacityText = settings.tankCapacityMl
                        ?.let { ml -> NumberInput.formatScaled(ml.toLong(), 3, trimTrailingZeros = true) }
                        .orEmpty(),
                )
            }
            revalidate()
        }
    }

    fun onNifChange(text: String) {
        _uiState.update { it.copy(nifText = text, saved = false) }
        revalidate()
    }

    fun onTankCapacityChange(text: String) {
        _uiState.update { it.copy(tankCapacityText = text, saved = false) }
        revalidate()
    }

    private fun revalidate() {
        val state = _uiState.value
        val errors = buildSet {
            if (state.nifText.isNotBlank() && !Nif.isValid(state.nifText)) add(SettingsError.INVALID_NIF)
            if (state.tankCapacityText.isNotBlank()) {
                val ml = NumberInput.parseLitersToMl(state.tankCapacityText)
                if (ml == null || ml <= 0 || ml > MAX_TANK_ML) add(SettingsError.INVALID_CAPACITY)
            }
        }
        _uiState.update { it.copy(errors = errors) }
    }

    fun onSaveClick() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.errors.isNotEmpty()) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            repository.setNif(Nif.normalize(state.nifText))
            repository.setTankCapacityMl(NumberInput.parseLitersToMl(state.tankCapacityText))
            _uiState.update { it.copy(saved = true, showErrors = false) }
        }
    }

    /** Nome sugerido para o ficheiro de exportação. */
    fun suggestedExportFileName(): String = "fueltracker-${LocalDate.now(clock)}.csv"

    fun exportTo(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferring = true) }
            try {
                val fillUps = fillUpRepository.observeAll().first()
                documentStore.writeText(uri, FillUpCsv.export(fillUps))
                transferEvents.send(TransferEvent.Exported(fillUps.size))
            } catch (e: Exception) {
                transferEvents.send(TransferEvent.Failed)
            } finally {
                _uiState.update { it.copy(isTransferring = false) }
            }
        }
    }

    fun importFrom(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferring = true) }
            try {
                val text = documentStore.readText(uri)
                val parsed = FillUpCsv.parse(text)
                if (parsed.fillUps.isEmpty() && parsed.errors.size == 1 && parsed.errors.single().line == 1) {
                    transferEvents.send(TransferEvent.HeaderInvalid(parsed.errors.single().reason))
                    return@launch
                }
                val existing = fillUpRepository.observeAll().first()
                val plan = FillUpCsv.planImport(existing, parsed.fillUps)
                plan.toInsert.forEach { fillUpRepository.insert(it) }
                transferEvents.send(TransferEvent.Imported(plan.toInsert.size, plan.skippedDuplicates, parsed.errors.size))
            } catch (e: Exception) {
                transferEvents.send(TransferEvent.Failed)
            } finally {
                _uiState.update { it.copy(isTransferring = false) }
            }
        }
    }

    private companion object {
        /** Limite generoso para apanhar erros de unidade (ex.: 4500 em vez de 45). */
        const val MAX_TANK_ML = 200_000
    }
}
