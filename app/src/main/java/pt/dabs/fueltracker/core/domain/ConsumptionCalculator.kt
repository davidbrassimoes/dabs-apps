package pt.dabs.fueltracker.core.domain

import pt.dabs.fueltracker.core.domain.model.ConsumptionCycle
import pt.dabs.fueltracker.core.domain.model.FillUp
import pt.dabs.fueltracker.core.domain.model.Period
import pt.dabs.fueltracker.core.domain.model.PeriodSummary
import pt.dabs.fueltracker.core.domain.model.StationSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Funções puras de cálculo de consumo e agregados. */
object ConsumptionCalculator {

    const val DEFAULT_RECENT_CYCLES = 5
    const val DEFAULT_ANOMALY_WINDOW = 5
    const val DEFAULT_ANOMALY_THRESHOLD_PERCENT = 20
    const val DEFAULT_ANOMALY_MIN_HISTORY = 2

    /** Ordem cronológica estável: data, depois odómetro, depois id. */
    fun sortChronologically(fillUps: List<FillUp>): List<FillUp> =
        fillUps.sortedWith(compareBy({ it.dateTime }, { it.odometerKm }, { it.id }))

    /**
     * Ciclos cheio-a-cheio em ordem cronológica.
     *
     * Um ciclo fecha em cada abastecimento com depósito cheio que tenha um cheio anterior.
     * Os litros e o custo de abastecimentos parciais entre dois cheios somam ao ciclo seguinte.
     * Ciclos com distância ≤ 0 (dados inconsistentes) são ignorados, mas os seus litros
     * continuam a acumular para o ciclo seguinte.
     */
    fun computeCycles(fillUps: List<FillUp>): List<ConsumptionCycle> {
        val sorted = sortChronologically(fillUps)
        val cycles = ArrayList<ConsumptionCycle>()
        var lastFull: FillUp? = null
        var pendingVolumeMl = 0L
        var pendingCostCents = 0L

        for (fillUp in sorted) {
            pendingVolumeMl += fillUp.volumeMl
            pendingCostCents += fillUp.totalCents
            if (!fillUp.isFullTank) continue

            val previous = lastFull
            if (previous != null) {
                val distanceKm = fillUp.odometerKm - previous.odometerKm
                if (distanceKm <= 0) {
                    // Dados inconsistentes: mantém o cheio anterior como início e acumula para o próximo.
                    continue
                }
                cycles += ConsumptionCycle(
                    fillUpId = fillUp.id,
                    startOdometerKm = previous.odometerKm,
                    endOdometerKm = fillUp.odometerKm,
                    volumeMl = pendingVolumeMl.toInt(),
                    costCents = pendingCostCents,
                    endDateTime = fillUp.dateTime,
                    consumptionMlPer100Km = FuelMath.consumptionMlPer100Km(pendingVolumeMl, distanceKm),
                    costMilliCentsPerKm = FuelMath.costMilliCentsPerKm(pendingCostCents, distanceKm),
                )
            }
            lastFull = fillUp
            pendingVolumeMl = 0L
            pendingCostCents = 0L
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
     * Autonomia estimada depois de atestar.
     *
     * Com capacidade do depósito e média recente conhecidas: capacidade / média.
     * Sem capacidade: distância média dos últimos [lastN] ciclos (o que o carro costuma fazer por depósito).
     * Devolve null quando não há dados suficientes.
     */
    fun estimateRangeKm(
        tankCapacityMl: Int?,
        cycles: List<ConsumptionCycle>,
        lastN: Int = DEFAULT_RECENT_CYCLES,
    ): RangeEstimate? {
        val recent = cycles.takeLast(lastN)
        val average = weightedAverageMlPer100Km(recent)
        if (tankCapacityMl != null && tankCapacityMl > 0 && average != null && average > 0) {
            return RangeEstimate(FuelMath.rangeKm(tankCapacityMl.toLong(), average), fromTankCapacity = true)
        }
        if (recent.isEmpty()) return null
        val averageDistance = FuelMath.roundDiv(recent.sumOf { it.distanceKm.toLong() }, recent.size.toLong())
        return RangeEstimate(averageDistance.toInt(), fromTankCapacity = false)
    }

    data class RangeEstimate(val km: Int, val fromTankCapacity: Boolean)

    /**
     * Odómetro previsto para o próximo abastecimento: o último odómetro mais a distância média
     * dos últimos [lastN] ciclos. Sem ciclos devolve o último odómetro; sem abastecimentos, null.
     */
    fun predictNextOdometerKm(fillUps: List<FillUp>, lastN: Int = DEFAULT_RECENT_CYCLES): Int? {
        val sorted = sortChronologically(fillUps)
        val last = sorted.lastOrNull() ?: return null
        val recent = computeCycles(sorted).takeLast(lastN)
        if (recent.isEmpty()) return last.odometerKm
        val averageDistance = FuelMath.roundDiv(recent.sumOf { it.distanceKm.toLong() }, recent.size.toLong())
        return last.odometerKm + averageDistance.toInt()
    }

    /**
     * Litros que, em média, ainda estão no depósito quando o utilizador atesta:
     * capacidade − média de litros dos últimos [lastN] abastecimentos cheios.
     * Devolve null sem capacidade, sem abastecimentos cheios, ou se a média exceder a capacidade
     * (sinal de capacidade mal configurada).
     */
    fun typicalReserveMl(
        tankCapacityMl: Int?,
        fillUps: List<FillUp>,
        lastN: Int = DEFAULT_RECENT_CYCLES,
    ): Int? {
        if (tankCapacityMl == null || tankCapacityMl <= 0) return null
        val recentFull = sortChronologically(fillUps).filter { it.isFullTank }.takeLast(lastN)
        if (recentFull.isEmpty()) return null
        val averageMl = FuelMath.roundDiv(recentFull.sumOf { it.volumeMl.toLong() }, recentFull.size.toLong())
        val reserve = tankCapacityMl - averageMl
        return if (reserve >= 0) reserve.toInt() else null
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
                    fillUpCount = inStation.size,
                    volumeMl = volume,
                    totalCents = total,
                    averagePricePerLiterMilliCents = if (volume > 0) FuelMath.averagePricePerLiterMilliCents(total, volume) else null,
                    lastDateTime = sorted.last().dateTime,
                )
            }
            .sortedWith(compareByDescending<StationSummary> { it.fillUpCount }.thenByDescending { it.lastDateTime })

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
