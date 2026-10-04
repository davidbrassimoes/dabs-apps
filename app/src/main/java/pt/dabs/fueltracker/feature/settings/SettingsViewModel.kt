package pt.dabs.fueltracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.dabs.fueltracker.core.data.SettingsRepository
import pt.dabs.fueltracker.core.domain.Nif
import pt.dabs.fueltracker.core.domain.NumberInput
import javax.inject.Inject

enum class SettingsError { INVALID_NIF, INVALID_CAPACITY }

data class SettingsUiState(
    val isLoading: Boolean = true,
    val nifText: String = "",
    val tankCapacityText: String = "",
    val errors: Set<SettingsError> = emptySet(),
    val showErrors: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = !isLoading && errors.isEmpty()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

    private companion object {
        /** Limite generoso para apanhar erros de unidade (ex.: 4500 em vez de 45). */
        const val MAX_TANK_ML = 200_000
    }
}
