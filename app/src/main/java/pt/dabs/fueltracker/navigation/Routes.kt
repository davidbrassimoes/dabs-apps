package pt.dabs.fueltracker.navigation

import kotlinx.serialization.Serializable
import pt.dabs.fueltracker.core.domain.model.FillUp

@Serializable
data object HomeRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

@Serializable
data object StatsRoute

/**
 * Leitura do talão; devolve ao ecrã anterior o texto do QR em [SCANNED_QR_KEY] e as linhas
 * reconhecidas por OCR (separadas por `\n`) em [SCANNED_TEXT_KEY]. Um QR vazio significa que o
 * talão foi lido só pelo texto (fotografia sem QR legível).
 */
@Serializable
data object ScanReceiptRoute

/**
 * Leitura do conta-km; devolve o total em [SCANNED_ODOMETER_KEY] (e o parcial, em décimas de km,
 * em [SCANNED_TRIP_KEY], se lido) ou `true` em
 * [ODOMETER_MANUAL_KEY] quando o utilizador prefere escrever à mão.
 * [lastOdometerKm] e [predictedKm] usam [NO_VALUE] quando desconhecidos.
 */
@Serializable
data class ScanOdometerRoute(val lastOdometerKm: Int = NO_VALUE, val predictedKm: Int = NO_VALUE)

const val NO_VALUE = -1
const val SCANNED_QR_KEY = "scanned_qr"
const val SCANNED_TEXT_KEY = "scanned_text"
const val SCANNED_ODOMETER_KEY = "scanned_odometer"
const val SCANNED_TRIP_KEY = "scanned_trip"
const val ODOMETER_MANUAL_KEY = "odometer_manual"

/**
 * Novo abastecimento quando [fillUpId] é [FillUp.NEW_ID]; edição caso contrário.
 * Com [startWithScan] o ecrã abre logo a leitura do talão (fluxo rápido do botão Abastecer).
 */
@Serializable
data class FillUpRoute(val fillUpId: Long = FillUp.NEW_ID, val startWithScan: Boolean = false)
