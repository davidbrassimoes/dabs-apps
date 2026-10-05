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
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val isTransferring: Boolean = false,
)

/** Resultado de uma exportação ou importação, para mostrar numa Snackbar. */
sealed interface TransferEvent {
    data class Exported(val count: Int) : TransferEvent
    data class Imported(val inserted: Int, val skippedDuplicates: Int, val invalidRows: Int) : TransferEvent
    data class HeaderInvalid(val reason: String) : TransferEvent
    data object Failed : TransferEvent
}

/** Definições: por agora só a cópia de segurança em CSV (exportar e importar). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val fillUpRepository: FillUpRepository,
    private val documentStore: DocumentStore,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val transferEvents = Channel<TransferEvent>(Channel.BUFFERED)
    val transferEventFlow: Flow<TransferEvent> = transferEvents.receiveAsFlow()

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
}
