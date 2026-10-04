package pt.dabs.fueltracker.navigation

import kotlinx.serialization.Serializable
import pt.dabs.fueltracker.core.domain.model.FillUp

@Serializable
data object HomeRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

/** Novo abastecimento quando [fillUpId] é [FillUp.NEW_ID]; edição caso contrário. */
@Serializable
data class FillUpRoute(val fillUpId: Long = FillUp.NEW_ID)
