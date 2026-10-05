package pt.dabs.fueltracker.core.domain.model

import java.time.YearMonth

/** Agregado por posto. [key] é o NIF do posto quando existe, senão o nome normalizado. */
data class StationSummary(
    val key: String,
    val name: String,
    val stationNif: String?,
    val brand: FuelBrand?,
    val fillUpCount: Int,
    val volumeMl: Long,
    val totalCents: Long,
    val averagePricePerLiterMilliCents: Int?,
    val lastDateTime: java.time.Instant,
)

sealed interface Period {
    data class Month(val yearMonth: YearMonth) : Period
    data class Year(val year: Int) : Period
}

/**
 * Agregado de um período (mês ou ano).
 *
 * Os totais de litros e € contam todos os abastecimentos cuja data cai no período.
 * A distância, e as médias dela derivadas, contam só os ciclos fechados no período
 * (média ponderada: litros dos ciclos / km dos ciclos, nunca média de médias).
 */
data class PeriodSummary(
    val period: Period,
    val fillUpCount: Int,
    val volumeMl: Long,
    val totalCents: Long,
    val distanceKm: Int,
    val cycleVolumeMl: Long,
    val cycleCostCents: Long,
    /** null quando não há ciclos fechados no período. */
    val averageConsumptionMlPer100Km: Int?,
    /** null quando não há ciclos fechados no período. */
    val costMilliCentsPerKm: Long?,
    /** null quando não há litros no período. */
    val averagePricePerLiterMilliCents: Int?,
)

/** Agregado por marca. */
data class BrandSummary(
    val brand: FuelBrand,
    val fillUpCount: Int,
    val volumeMl: Long,
    val totalCents: Long,
    val averagePricePerLiterMilliCents: Int?,
)

/**
 * Agregado por combustível ([type] e [premium], null = aditivado desconhecido). O consumo médio é
 * dos [cycleCount] ciclos começados com este combustível; null sem ciclos.
 */
data class FuelSummary(
    val type: FuelType,
    val premium: Boolean?,
    val fillUpCount: Int,
    val averagePricePerLiterMilliCents: Int?,
    val cycleCount: Int,
    val averageConsumptionMlPer100Km: Int?,
)
