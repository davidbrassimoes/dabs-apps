package pt.dabs.fueltracker.core.domain.model

import java.time.Instant

/** De onde vem a distância de um ciclo. */
enum class DistanceSource {
    /** Conta-km total menos o do cheio anterior. */
    ODOMETER,
    /** Conta-km parcial lido ao abastecer (posto a zeros no abastecimento anterior). */
    TRIP,
}

/**
 * Um ciclo cheio-a-cheio, fechado pelo abastecimento com id [fillUpId].
 *
 * [volumeMl] e [costCents] somam todos os abastecimentos depois do cheio anterior
 * até (e incluindo) o que fecha o ciclo, para suportar abastecimentos parciais.
 * Num ciclo medido pelo parcial sem cheio anterior registado, [startOdometerKm] é estimado
 * (fim − parcial).
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
    val distanceSource: DistanceSource = DistanceSource.ODOMETER,
    /**
     * true quando o parcial e o conta-km total discordam (faltou registar um abastecimento, ou o
     * parcial não foi posto a zeros); a distância usada é a mais provável das duas.
     */
    val tripMismatch: Boolean = false,
) {
    val distanceKm: Int get() = endOdometerKm - startOdometerKm
}
