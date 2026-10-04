package pt.dabs.fueltracker.navigation

import kotlinx.serialization.Serializable
import pt.dabs.fueltracker.core.domain.model.FillUp

@Serializable
data object HomeRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

/** Leitura do QR do talão; devolve o texto lido ao ecrã anterior em [SCANNED_QR_KEY]. */
@Serializable
data object ScanReceiptRoute

const val SCANNED_QR_KEY = "scanned_qr"

/** Novo abastecimento quando [fillUpId] é [FillUp.NEW_ID]; edição caso contrário. */
@Serializable
data class FillUpRoute(val fillUpId: Long = FillUp.NEW_ID)
