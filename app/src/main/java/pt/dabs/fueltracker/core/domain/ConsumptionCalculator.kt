package pt.dabs.fueltracker.core.domain

import pt.dabs.fueltracker.core.domain.model.ConsumptionCycle
import pt.dabs.fueltracker.core.domain.model.DistanceSource
import pt.dabs.fueltracker.core.domain.model.FillUp
import pt.dabs.fueltracker.core.domain.model.BrandSummary
import pt.dabs.fueltracker.core.domain.model.FuelSummary
import pt.dabs.fueltracker.core.domain.model.Period
import pt.dabs.fueltracker.core.domain.model.PeriodSummary
import pt.dabs.fueltracker.core.domain.model.StationSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/** Funções puras de cálculo de consumo e agregados. */
object ConsumptionCalculator {

    const val DEFAULT_RECENT_CYCLES = 5
    const val DEFAULT_ANOMALY_WINDOW = 5
    const val DEFAULT_ANOMALY_THRESHOLD_PERCENT = 20
    const val DEFAULT_ANOMALY_MIN_HISTORY = 2

    /** Ordem cronológica estável: data, depois odómetro, depois id. */
    fun sortChronologically(fillUps: List<FillUp>): List<FillUp> =
        fillUps.sortedWith(compareBy({ it.dateTime }, { it.odometerKm }, { it.id }))

    /** Diferença admitida entre o parcial e o conta-km total (o total não tem décimas). */
    const val TRIP_TOLERANCE_KM = 2

    /**
     * Ciclos cheio-a-cheio em ordem cronológica.
     *
     * Um ciclo fecha em cada abastecimento com depósito cheio. Os litros e o custo de
     * abastecimentos parciais entre dois cheios somam ao ciclo seguinte.
     *
     * A distância vem do conta-km total (fim − cheio anterior) e, quando o abastecimento tem o
     * parcial registado (posto a zeros no abastecimento anterior), é confrontada com ele:
     * - batem (±[TRIP_TOLERANCE_KM]): distância do conta-km;
     * - parcial claramente menor: faltou registar um abastecimento pelo meio (o total abrange dois
     *   ciclos com os litros de um só) → distância do parcial, marcada como divergente;
     * - parcial claramente maior: não foi posto a zeros → distância do conta-km, marcada como divergente;
     * - sem cheio anterior registado (o primeiro abastecimento): distância do parcial.
     * O parcial só conta quando o ciclo é um único abastecimento (sem parciais pelo meio).
     *
     * Ciclos sem distância positiva (dados inconsistentes) são ignorados, mas os seus litros
     * continuam a acumular para o ciclo seguinte.
     */
    fun computeCycles(fillUps: List<FillUp>): List<ConsumptionCycle> {
        val sorted = sortChronologically(fillUps)
        val cycles = ArrayList<ConsumptionCycle>()
        var lastFull: FillUp? = null
        var pendingVolumeMl = 0L
        var pendingCostCents = 0L
        var pendingCount = 0

        for (fillUp in sorted) {
            pendingVolumeMl += fillUp.volumeMl
            pendingCostCents += fillUp.totalCents
            pendingCount++
            if (!fillUp.isFullTank) continue

            val tripKm = fillUp.tripTenthsKm
                ?.takeIf { it > 0 && pendingCount == 1 }
                ?.let { FuelMath.roundDiv(it.toLong(), 10).toInt() }
                ?.takeIf { it > 0 }
            val odometerKm = lastFull?.let { fillUp.odometerKm - it.odometerKm }?.takeIf { it > 0 }
            val (distanceKm, source, mismatch) = when {
                odometerKm != null && tripKm != null -> when {
                    abs(tripKm - odometerKm) <= TRIP_TOLERANCE_KM -> Triple(odometerKm, DistanceSource.ODOMETER, false)
                    tripKm < odometerKm -> Triple(tripKm, DistanceSource.TRIP, true)
                    else -> Triple(odometerKm, DistanceSource.ODOMETER, true)
                }
                odometerKm != null -> Triple(odometerKm, DistanceSource.ODOMETER, false)
                tripKm != null -> Triple(tripKm, DistanceSource.TRIP, lastFull != null)
                else -> null
            } ?: run {
                // Sem distância: o primeiro abastecimento sem parcial, ou dados inconsistentes
                // (mantém o cheio anterior como início e acumula para o próximo).
                if (lastFull == null) {
                    lastFull = fillUp
                    pendingVolumeMl = 0L
                    pendingCostCents = 0L
                    pendingCount = 0
                }
                continue
            }
            cycles += ConsumptionCycle(
                fillUpId = fillUp.id,
                startOdometerKm = fillUp.odometerKm - distanceKm,
                endOdometerKm = fillUp.odometerKm,
                volumeMl = pendingVolumeMl.toInt(),
                costCents = pendingCostCents,
                endDateTime = fillUp.dateTime,
                consumptionMlPer100Km = FuelMath.consumptionMlPer100Km(pendingVolumeMl, distanceKm),
                costMilliCentsPerKm = FuelMath.costMilliCentsPerKm(pendingCostCents, distanceKm),
                distanceSource = source,
                tripMismatch = mismatch,
            )
            lastFull = fillUp
            pendingVolumeMl = 0L
            pendingCostCents = 0L
            pendingCount = 0
        }
        return cycles
    }

    /** Média ponderada (litros totais / km totais) dos ciclos dados; null se não houver ciclos. */
    fun weightedAverageMlPer100Km(cycles: List<ConsumptionCycle>): Int? {
        if (cycles.isEmpty()) return null
        val volume = cycles.sumOf { it.volumeMl.toLong() }
        val distance = cycles.sumOf { it.distanceKm }
        if (distance <= 0) return null
        return FuelMath.consumptionMlPer100Km(volume, distance)
    }

    /** Média ponderada dos últimos [lastN] ciclos. */
    fun recentAverageMlPer100Km(
        cycles: List<ConsumptionCycle>,
        lastN: Int = DEFAULT_RECENT_CYCLES,
    ): Int? = weightedAverageMlPer100Km(cycles.takeLast(lastN))

    /**
     * Distância habitual por depósito: média dos últimos [lastN] ciclos. Como o utilizador atesta
     * sempre logo depois da reserva, é a autonomia útil (até à reserva); null sem ciclos.
     */
    fun typicalDistancePerTankKm(cycles: List<ConsumptionCycle>, lastN: Int = DEFAULT_RECENT_CYCLES): Int? {
        val recent = cycles.takeLast(lastN)
        if (recent.isEmpty()) return null
        return FuelMath.roundDiv(recent.sumOf { it.distanceKm.toLong() }, recent.size.toLong()).toInt()
    }

    /**
     * Odómetro previsto para o próximo abastecimento: o último odómetro mais a distância média
     * dos últimos [lastN] ciclos. Sem ciclos devolve o último odómetro; sem abastecimentos, null.
     */
    fun predictNextOdometerKm(fillUps: List<FillUp>, lastN: Int = DEFAULT_RECENT_CYCLES): Int? {
        val sorted = sortChronologically(fillUps)
        val last = sorted.lastOrNull() ?: return null
        val averageDistance = typicalDistancePerTankKm(computeCycles(sorted), lastN) ?: return last.odometerKm
        return last.odometerKm + averageDistance
    }

    /**
     * Ids dos abastecimentos cujo ciclo tem consumo acima de (100 + [thresholdPercent])% da
     * média móvel ponderada dos [window] ciclos anteriores. Precisa de pelo menos [minHistory]
     * ciclos anteriores para haver comparação.
     */
    fun detectAnomalies(
        cycles: List<ConsumptionCycle>,
        window: Int = DEFAULT_ANOMALY_WINDOW,
        thresholdPercent: Int = DEFAULT_ANOMALY_THRESHOLD_PERCENT,
        minHistory: Int = DEFAULT_ANOMALY_MIN_HISTORY,
    ): Set<Long> {
        val result = LinkedHashSet<Long>()
        for (index in cycles.indices) {
            if (index < minHistory) continue
            val baselineCycles = cycles.subList(maxOf(0, index - window), index)
            val baseline = weightedAverageMlPer100Km(baselineCycles) ?: continue
            val limit = baseline.toLong() * (100 + thresholdPercent)
            if (cycles[index].consumptionMlPer100Km.toLong() * 100 > limit) {
                result += cycles[index].fillUpId
            }
        }
        return result
    }

    /** Agregados por mês, por ordem cronológica, só para meses com abastecimentos. */
    fun monthlySummaries(fillUps: List<FillUp>, zone: ZoneId): List<PeriodSummary> {
        val cycles = computeCycles(fillUps)
        return fillUps
            .groupBy { YearMonth.from(it.dateTime.atZone(zone).toLocalDate()) }
            .toSortedMap()
            .map { (yearMonth, inMonth) ->
                summarise(Period.Month(yearMonth), inMonth, cycles.filter {
                    YearMonth.from(it.endDateTime.atZone(zone).toLocalDate()) == yearMonth
                })
            }
    }

    /** Agregados por ano, por ordem cronológica, só para anos com abastecimentos. */
    fun yearlySummaries(fillUps: List<FillUp>, zone: ZoneId): List<PeriodSummary> {
        val cycles = computeCycles(fillUps)
        return fillUps
            .groupBy { it.dateTime.atZone(zone).year }
            .toSortedMap()
            .map { (year, inYear) ->
                summarise(Period.Year(year), inYear, cycles.filter { it.endDateTime.atZone(zone).year == year })
            }
    }

    /** Agregado do mês de [today]; vazio (zeros) se não houver abastecimentos nesse mês. */
    fun monthSummary(fillUps: List<FillUp>, today: LocalDate, zone: ZoneId): PeriodSummary {
        val yearMonth = YearMonth.from(today)
        return monthlySummaries(fillUps, zone).firstOrNull { it.period == Period.Month(yearMonth) }
            ?: summarise(Period.Month(yearMonth), emptyList(), emptyList())
    }

    /**
     * Agregados por posto, do mais frequentado para o menos. Agrupa pelo NIF do posto quando existe,
     * senão pelo nome (sem distinção de maiúsculas); abastecimentos sem posto ficam de fora.
     */
    fun stationSummaries(fillUps: List<FillUp>): List<StationSummary> =
        fillUps
            .mapNotNull { fillUp ->
                val nif = fillUp.stationNif?.takeIf { it.isNotBlank() }
                val name = fillUp.stationName?.trim()?.takeIf { it.isNotEmpty() }
                val key = nif ?: name?.lowercase() ?: return@mapNotNull null
                key to fillUp
            }
            .groupBy({ it.first }, { it.second })
            .map { (key, inStation) ->
                val sorted = sortChronologically(inStation)
                val volume = inStation.sumOf { it.volumeMl.toLong() }
                val total = inStation.sumOf { it.totalCents }
                StationSummary(
                    key = key,
                    name = sorted.lastOrNull { !it.stationName.isNullOrBlank() }?.stationName?.trim()
                        ?: sorted.last().stationNif.orEmpty(),
                    stationNif = sorted.lastOrNull { !it.stationNif.isNullOrBlank() }?.stationNif,
                    brand = sorted.lastOrNull { it.brand != null }?.brand,
                    fillUpCount = inStation.size,
                    volumeMl = volume,
                    totalCents = total,
                    averagePricePerLiterMilliCents = if (volume > 0) FuelMath.averagePricePerLiterMilliCents(total, volume) else null,
                    lastDateTime = sorted.last().dateTime,
                )
            }
            .sortedWith(compareByDescending<StationSummary> { it.fillUpCount }.thenByDescending { it.lastDateTime })

    /** Agregados por marca (só abastecimentos com marca), do mais frequente para o menos. */
    fun brandSummaries(fillUps: List<FillUp>): List<BrandSummary> =
        fillUps.filter { it.brand != null }
            .groupBy { it.brand!! }
            .map { (brand, inBrand) ->
                val volume = inBrand.sumOf { it.volumeMl.toLong() }
                val total = inBrand.sumOf { it.totalCents }
                BrandSummary(
                    brand = brand,
                    fillUpCount = inBrand.size,
                    volumeMl = volume,
                    totalCents = total,
                    averagePricePerLiterMilliCents = if (volume > 0) FuelMath.averagePricePerLiterMilliCents(total, volume) else null,
                )
            }
            .sortedWith(compareByDescending<BrandSummary> { it.fillUpCount }.thenByDescending { it.volumeMl })

    /**
     * Agregados por combustível (tipo e aditivado). O preço médio conta os abastecimentos com esse
     * combustível. O consumo conta os ciclos **começados** por ele: o combustível queimado num ciclo
     * é o que se meteu no cheio anterior, não o que fecha o ciclo. Ciclos sem cheio anterior
     * registado (medidos só pelo parcial) não contam para o consumo.
     */
    fun fuelSummaries(fillUps: List<FillUp>): List<FuelSummary> {
        val sorted = sortChronologically(fillUps)
        val fullTanks = sorted.filter { it.isFullTank }
        val fullIndex = fullTanks.withIndex().associate { (index, fillUp) -> fillUp.id to index }
        val cyclesByFuel = computeCycles(sorted).mapNotNull { cycle ->
            val closing = fullIndex[cycle.fillUpId] ?: return@mapNotNull null
            val start = fullTanks.getOrNull(closing - 1) ?: return@mapNotNull null
            if (start.fuelType == null) return@mapNotNull null
            (start.fuelType to start.fuelPremium) to cycle
        }.groupBy({ it.first }, { it.second })

        return sorted.filter { it.fuelType != null }
            .groupBy { it.fuelType!! to it.fuelPremium }
            .map { (key, withFuel) ->
                val volume = withFuel.sumOf { it.volumeMl.toLong() }
                val total = withFuel.sumOf { it.totalCents }
                val cycles = cyclesByFuel[key].orEmpty()
                FuelSummary(
                    type = key.first,
                    premium = key.second,
                    fillUpCount = withFuel.size,
                    averagePricePerLiterMilliCents = if (volume > 0) FuelMath.averagePricePerLiterMilliCents(total, volume) else null,
                    cycleCount = cycles.size,
                    averageConsumptionMlPer100Km = weightedAverageMlPer100Km(cycles),
                )
            }
            .sortedWith(compareByDescending<FuelSummary> { it.fillUpCount }.thenBy { it.type.ordinal })
    }

    private fun summarise(period: Period, fillUps: List<FillUp>, cycles: List<ConsumptionCycle>): PeriodSummary {
        val volumeMl = fillUps.sumOf { it.volumeMl.toLong() }
        val totalCents = fillUps.sumOf { it.totalCents }
        val distanceKm = cycles.sumOf { it.distanceKm }
        val cycleVolumeMl = cycles.sumOf { it.volumeMl.toLong() }
        val cycleCostCents = cycles.sumOf { it.costCents }
        return PeriodSummary(
            period = period,
            fillUpCount = fillUps.size,
            volumeMl = volumeMl,
            totalCents = totalCents,
            distanceKm = distanceKm,
            cycleVolumeMl = cycleVolumeMl,
            cycleCostCents = cycleCostCents,
            averageConsumptionMlPer100Km = if (distanceKm > 0) FuelMath.consumptionMlPer100Km(cycleVolumeMl, distanceKm) else null,
            costMilliCentsPerKm = if (distanceKm > 0) FuelMath.costMilliCentsPerKm(cycleCostCents, distanceKm) else null,
            averagePricePerLiterMilliCents = if (volumeMl > 0) FuelMath.averagePricePerLiterMilliCents(totalCents, volumeMl) else null,
        )
    }
}
