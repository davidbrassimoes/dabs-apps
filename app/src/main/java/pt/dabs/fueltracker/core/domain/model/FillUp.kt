package pt.dabs.fueltracker.core.domain.model

import java.time.Instant

/**
 * Um abastecimento. Todos os valores monetários e volumes são inteiros:
 * - [volumeMl]: mililitros (litros × 1000)
 * - [pricePerLiterMilliCents]: milésimos de cêntimo por litro (1,749 €/L = 174 900)
 * - [totalCents]: cêntimos
 */
data class FillUp(
    val id: Long = NEW_ID,
    val dateTime: Instant,
    val odometerKm: Int,
    val volumeMl: Int,
    val pricePerLiterMilliCents: Int,
    val totalCents: Long,
    val isFullTank: Boolean = true,
    val stationNif: String? = null,
    val stationName: String? = null,
    val atcud: String? = null,
    val buyerNifPresent: Boolean? = null,
    val notes: String? = null,
    val createdAt: Instant,
) {
    companion object {
        const val NEW_ID = 0L
    }
}
