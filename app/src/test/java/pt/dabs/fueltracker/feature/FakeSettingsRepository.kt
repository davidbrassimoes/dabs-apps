package pt.dabs.fueltracker.feature

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import pt.dabs.fueltracker.core.data.SettingsRepository
import pt.dabs.fueltracker.core.data.UserSettings

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    val current: UserSettings get() = state.value

    override val settings: Flow<UserSettings> = state

    override suspend fun setNif(nif: String?) {
        state.update { it.copy(nif = nif?.takeIf { n -> n.isNotBlank() }) }
    }
}
