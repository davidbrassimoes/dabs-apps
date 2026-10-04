package pt.dabs.fueltracker.navigation

import kotlinx.serialization.Serializable
import pt.dabs.fueltracker.core.domain.model.FillUp

@Serializable
data object HomeRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

/**
 * Leitura do talão; devolve ao ecrã anterior o texto do QR em [SCANNED_QR_KEY] e as linhas
 * reconhecidas por OCR (separadas por `\n`) em [SCANNED_TEXT_KEY].
 */
@Serializable
data object ScanReceiptRoute

const val SCANNED_QR_KEY = "scanned_qr"
const val SCANNED_TEXT_KEY = "scanned_text"

/** Novo abastecimento quando [fillUpId] é [FillUp.NEW_ID]; edição caso contrário. */
@Serializable
data class FillUpRoute(val fillUpId: Long = FillUp.NEW_ID)
