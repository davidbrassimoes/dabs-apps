package pt.dabs.fueltracker.core.domain.model

import java.time.Instant

/**
 * Um ciclo cheio-a-cheio, fechado pelo abastecimento com id [fillUpId].
 *
 * [volumeMl] e [costCents] somam todos os abastecimentos depois do cheio anterior
 * até (e incluindo) o que fecha o ciclo, para suportar abastecimentos parciais.
 */
data class ConsumptionCycle(
    val fillUpId: Long,
    val startOdometerKm: Int,
    val endOdometerKm: Int,
    val volumeMl: Int,
    val costCents: Long,
    val endDateTime: Instant,
    /** Litros/100 km em mililitros (6,67 L/100 km = 6670). */
    val consumptionMlPer100Km: Int,
    /** €/km em milésimos de cêntimo (0,117 €/km = 11 700). */
    val costMilliCentsPerKm: Long,
) {
    val distanceKm: Int get() = endOdometerKm - startOdometerKm
}
