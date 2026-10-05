package pt.dabs.fueltracker.core.data

import kotlinx.coroutines.flow.Flow

data class UserSettings(
    /** NIF do utilizador, só dígitos; null se não configurado. */
    val nif: String? = null,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setNif(nif: String?)
}
