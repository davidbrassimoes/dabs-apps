package pt.dabs.fueltracker.core.data

import kotlinx.coroutines.flow.Flow

data class UserSettings(
    /** NIF do utilizador, só dígitos; null se não configurado. */
    val nif: String? = null,
    /** Capacidade do depósito em ml; null se não configurada. */
    val tankCapacityMl: Int? = null,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setNif(nif: String?)
    suspend fun setTankCapacityMl(capacityMl: Int?)
}
