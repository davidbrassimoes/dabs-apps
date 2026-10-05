package pt.dabs.fueltracker.feature.nif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pt.dabs.fueltracker.core.data.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class ShowNifViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val nif: StateFlow<String?> = settingsRepository.settings
        .map { it.nif }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
