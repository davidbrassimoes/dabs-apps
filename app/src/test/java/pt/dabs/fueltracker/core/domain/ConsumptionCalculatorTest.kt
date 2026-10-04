package pt.dabs.fueltracker.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.dabs.fueltracker.core.domain.model.Period
import java.time.LocalDate
import java.time.YearMonth

class ConsumptionCalculatorTest {

    // --- ciclos -----------------------------------------------------------------

    @Test
    fun `no fill ups means no cycles`() {
        assertTrue(ConsumptionCalculator.computeCycles(emptyList()).isEmpty())
    }

    @Test
    fun `single fill up has no cycle`() {
        val cycles = ConsumptionCalculator.computeCycles(listOf(fillUp(1, "2026-01-05", 10_000, 40_000)))
        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `two full fill ups make one cycle`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-05", 10_000, 40_000),
                fillUp(2, "2026-01-20", 10_600, 39_000, 174_900),
            )
        )
        assertEquals(1, cycles.size)
        val cycle = cycles.single()
        assertEquals(2L, cycle.fillUpId)
        assertEquals(10_000, cycle.startOdometerKm)
        assertEquals(10_600, cycle.endOdometerKm)
        assertEquals(600, cycle.distanceKm)
        assertEquals(39_000, cycle.volumeMl)
        assertEquals(6_500, cycle.consumptionMlPer100Km)
        assertEquals(FuelMath.totalCents(39_000, 174_900), cycle.costCents)
        assertEquals(FuelMath.costMilliCentsPerKm(cycle.costCents, 600), cycle.costMilliCentsPerKm)
    }

    @Test
    fun `cycles are computed in date order regardless of input order`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(3, "2026-03-01", 12_000, 30_000),
                fillUp(1, "2026-01-01", 10_000, 40_000),
                fillUp(2, "2026-02-01", 11_000, 35_000),
            )
        )
        assertEquals(listOf(2L, 3L), cycles.map { it.fillUpId })
        assertEquals(listOf(1_000, 1_000), cycles.map { it.distanceKm })
        assertEquals(listOf(3_500, 3_000), cycles.map { it.consumptionMlPer100Km })
    }

    @Test
    fun `partial fill ups add their volume to the next full cycle`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-01", 10_000, 40_000),
                fillUp(2, "2026-01-10", 10_300, 15_000, isFullTank = false),
                fillUp(3, "2026-01-15", 10_500, 10_000, isFullTank = false),
                fillUp(4, "2026-01-25", 11_000, 30_000),
            )
        )
        assertEquals(1, cycles.size)
        val cycle = cycles.single()
        assertEquals(4L, cycle.fillUpId)
        assertEquals(1_000, cycle.distanceKm)
        assertEquals(55_000, cycle.volumeMl)
        assertEquals(5_500, cycle.consumptionMlPer100Km)
    }

    @Test
    fun `leading partial fill ups before the first full one are dropped`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-01", 10_000, 20_000, isFullTank = false),
                fillUp(2, "2026-01-05", 10_200, 40_000),
                fillUp(3, "2026-01-20", 10_800, 36_000),
            )
        )
        assertEquals(1, cycles.size)
        assertEquals(36_000, cycles.single().volumeMl)
        assertEquals(600, cycles.single().distanceKm)
    }

    @Test
    fun `trailing partial fill up does not close a cycle`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-01", 10_000, 40_000),
                fillUp(2, "2026-01-20", 10_600, 36_000),
                fillUp(3, "2026-01-25", 10_900, 10_000, isFullTank = false),
            )
        )
        assertEquals(listOf(2L), cycles.map { it.fillUpId })
    }

    @Test
    fun `cycle with zero or negative distance is skipped but volume carries over`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-01", 10_000, 40_000),
                fillUp(2, "2026-01-02", 10_000, 5_000),   // mesmo odómetro: ciclo inválido
                fillUp(3, "2026-01-20", 10_500, 30_000),
            )
        )
        assertEquals(listOf(3L), cycles.map { it.fillUpId })
        assertEquals(35_000, cycles.single().volumeMl)
        assertEquals(500, cycles.single().distanceKm)
        assertEquals(7_000, cycles.single().consumptionMlPer100Km)
    }

    @Test
    fun `same day fill ups are ordered by odometer`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-01", 10_500, 30_000, time = "10:00"),
                fillUp(2, "2026-01-01", 10_000, 40_000, time = "10:00"),
            )
        )
        assertEquals(1, cycles.size)
        assertEquals(1L, cycles.single().fillUpId)
        assertEquals(500, cycles.single().distanceKm)
    }

    // --- médias -----------------------------------------------------------------

    @Test
    fun `weighted average is total liters over total km, not mean of means`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-01", 10_000, 40_000),
                fillUp(2, "2026-01-10", 10_100, 10_000),  // 10 L/100 km em 100 km
                fillUp(3, "2026-01-20", 11_100, 50_000),  // 5 L/100 km em 1000 km
            )
        )
        // média de médias seria 7,5; ponderada é 60 L / 1100 km = 5,4545
        assertEquals(5_455, ConsumptionCalculator.weightedAverageMlPer100Km(cycles))
    }

    @Test
    fun `recent average uses only the last n cycles`() {
        val fillUps = listOf(
            fillUp(1, "2026-01-01", 10_000, 40_000),
            fillUp(2, "2026-01-10", 10_500, 50_000),  // 10 L/100
            fillUp(3, "2026-01-20", 11_000, 30_000),  // 6
            fillUp(4, "2026-01-30", 11_500, 30_000),  // 6
        )
        val cycles = ConsumptionCalculator.computeCycles(fillUps)
        assertEquals(6_000, ConsumptionCalculator.recentAverageMlPer100Km(cycles, lastN = 2))
        assertEquals(7_333, ConsumptionCalculator.recentAverageMlPer100Km(cycles, lastN = 3))
        assertEquals(7_333, ConsumptionCalculator.recentAverageMlPer100Km(cycles, lastN = 10))
    }

    @Test
    fun `averages are null without cycles`() {
        assertNull(ConsumptionCalculator.weightedAverageMlPer100Km(emptyList()))
        assertNull(ConsumptionCalculator.recentAverageMlPer100Km(emptyList()))
    }

    // --- autonomia --------------------------------------------------------------

    @Test
    fun `range uses tank capacity when known`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-01", 10_000, 40_000),
                fillUp(2, "2026-01-10", 10_600, 39_000), // 6,5 L/100
            )
        )
        val estimate = ConsumptionCalculator.estimateRangeKm(45_000, cycles)
        assertNotNull(estimate)
        assertEquals(692, estimate!!.km)
        assertTrue(estimate.fromTankCapacity)
    }

    @Test
    fun `range falls back to average cycle distance without capacity`() {
        val cycles = ConsumptionCalculator.computeCycles(
            listOf(
                fillUp(1, "2026-01-01", 10_000, 40_000),
                fillUp(2, "2026-01-10", 10_600, 39_000),
                fillUp(3, "2026-01-20", 11_300, 39_000),
            )
        )
        val estimate = ConsumptionCalculator.estimateRangeKm(null, cycles)
        assertNotNull(estimate)
        assertEquals(650, estimate!!.km)
        assertFalse(estimate.fromTankCapacity)
    }

    @Test
    fun `range is null without cycles`() {
        assertNull(ConsumptionCalculator.estimateRangeKm(45_000, emptyList()))
        assertNull(ConsumptionCalculator.estimateRangeKm(null, emptyList()))
    }

    // --- anomalias --------------------------------------------------------------

    @Test
    fun `detects a cycle more than 20 percent above the moving average`() {
        val fillUps = listOf(
            fillUp(1, "2026-01-01", 10_000, 40_000),
            fillUp(2, "2026-01-10", 10_500, 30_000),  // 6,0
            fillUp(3, "2026-01-20", 11_000, 31_000),  // 6,2
            fillUp(4, "2026-01-30", 11_500, 40_000),  // 8,0 → anomalia (média 6,1 × 1,2 = 7,32)
            fillUp(5, "2026-02-10", 12_000, 32_000),  // 6,4 → normal (média de 6,0/6,2/8,0 = 6,73)
        )
        val cycles = ConsumptionCalculator.computeCycles(fillUps)
        assertEquals(setOf(4L), ConsumptionCalculator.detectAnomalies(cycles))
    }

    @Test
    fun `needs minimum history before flagging`() {
        val fillUps = listOf(
            fillUp(1, "2026-01-01", 10_000, 40_000),
            fillUp(2, "2026-01-10", 10_500, 30_000),  // 6,0
            fillUp(3, "2026-01-20", 11_000, 50_000),  // 10,0 mas só há um ciclo anterior
        )
        val cycles = ConsumptionCalculator.computeCycles(fillUps)
        assertTrue(ConsumptionCalculator.detectAnomalies(cycles).isEmpty())
        assertEquals(setOf(3L), ConsumptionCalculator.detectAnomalies(cycles, minHistory = 1))
    }

    @Test
    fun `exactly at the threshold is not an anomaly`() {
        val fillUps = listOf(
            fillUp(1, "2026-01-01", 10_000, 40_000),
            fillUp(2, "2026-01-10", 11_000, 50_000),  // 5,0
            fillUp(3, "2026-01-20", 12_000, 50_000),  // 5,0
            fillUp(4, "2026-01-30", 13_000, 60_000),  // 6,0 = exatamente +20 %
        )
        val cycles = ConsumptionCalculator.computeCycles(fillUps)
        assertTrue(ConsumptionCalculator.detectAnomalies(cycles).isEmpty())
    }

    // --- agregados --------------------------------------------------------------

    private val threeMonths = listOf(
        fillUp(1, "2025-12-28", 10_000, 40_000, 170_000),            // dezembro: só abastece, sem ciclo
        fillUp(2, "2026-01-10", 10_600, 36_000, 175_000),            // janeiro: ciclo 600 km / 36 L
        fillUp(3, "2026-01-25", 11_100, 30_000, 180_000),            // janeiro: ciclo 500 km / 30 L
        fillUp(4, "2026-02-14", 11_800, 42_000, 165_000),            // fevereiro: ciclo 700 km / 42 L
    )

    @Test
    fun `monthly summaries only for months with fill ups, in order`() {
        val months = ConsumptionCalculator.monthlySummaries(threeMonths, LISBON)
        assertEquals(
            listOf(YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2)),
            months.map { (it.period as Period.Month).yearMonth }
        )
    }

    @Test
    fun `month without cycles has totals but null averages`() {
        val december = ConsumptionCalculator.monthlySummaries(threeMonths, LISBON).first()
        assertEquals(1, december.fillUpCount)
        assertEquals(40_000L, december.volumeMl)
        assertEquals(6_800L, december.totalCents)
        assertEquals(0, december.distanceKm)
        assertNull(december.averageConsumptionMlPer100Km)
        assertNull(december.costMilliCentsPerKm)
        assertEquals(170_000, december.averagePricePerLiterMilliCents)
    }

    @Test
    fun `month aggregates are weighted across its cycles`() {
        val january = ConsumptionCalculator.monthlySummaries(threeMonths, LISBON)[1]
        assertEquals(2, january.fillUpCount)
        assertEquals(66_000L, january.volumeMl)
        assertEquals(1_100, january.distanceKm)
        assertEquals(66_000L, january.cycleVolumeMl)
        assertEquals(6_000, january.averageConsumptionMlPer100Km) // 66 L / 1100 km
        val expectedCost = FuelMath.totalCents(36_000, 175_000) + FuelMath.totalCents(30_000, 180_000)
        assertEquals(expectedCost, january.totalCents)
        assertEquals(FuelMath.costMilliCentsPerKm(expectedCost, 1_100), january.costMilliCentsPerKm)
        assertEquals(FuelMath.averagePricePerLiterMilliCents(expectedCost, 66_000), january.averagePricePerLiterMilliCents)
    }

    @Test
    fun `yearly summaries cross the year boundary correctly`() {
        val years = ConsumptionCalculator.yearlySummaries(threeMonths, LISBON)
        assertEquals(listOf(2025, 2026), years.map { (it.period as Period.Year).year })
        val y2025 = years[0]
        assertEquals(1, y2025.fillUpCount)
        assertEquals(0, y2025.distanceKm)
        val y2026 = years[1]
        assertEquals(3, y2026.fillUpCount)
        assertEquals(1_800, y2026.distanceKm)
        assertEquals(108_000L, y2026.cycleVolumeMl)
        assertEquals(6_000, y2026.averageConsumptionMlPer100Km)
    }

    @Test
    fun `month summary for a month without data is empty`() {
        val summary = ConsumptionCalculator.monthSummary(threeMonths, LocalDate.of(2026, 5, 10), LISBON)
        assertEquals(Period.Month(YearMonth.of(2026, 5)), summary.period)
        assertEquals(0, summary.fillUpCount)
        assertEquals(0L, summary.totalCents)
        assertNull(summary.averageConsumptionMlPer100Km)
        assertNull(summary.averagePricePerLiterMilliCents)
    }

    @Test
    fun `month summary for the current month`() {
        val summary = ConsumptionCalculator.monthSummary(threeMonths, LocalDate.of(2026, 2, 20), LISBON)
        assertEquals(700, summary.distanceKm)
        assertEquals(6_000, summary.averageConsumptionMlPer100Km)
    }

    @Test
    fun `time zone decides the month of a late night fill up`() {
        // 23:30 em Lisboa a 31 de janeiro é já 1 de fevereiro em Atenas.
        val fillUps = listOf(
            fillUp(1, "2026-01-31", 10_000, 40_000, time = "23:30"),
        )
        val lisbon = ConsumptionCalculator.monthlySummaries(fillUps, LISBON).single()
        val athens = ConsumptionCalculator.monthlySummaries(fillUps, java.time.ZoneId.of("Europe/Athens")).single()
        assertEquals(YearMonth.of(2026, 1), (lisbon.period as Period.Month).yearMonth)
        assertEquals(YearMonth.of(2026, 2), (athens.period as Period.Month).yearMonth)
    }

    @Test
    fun `summaries of empty list are empty`() {
        assertTrue(ConsumptionCalculator.monthlySummaries(emptyList(), LISBON).isEmpty())
        assertTrue(ConsumptionCalculator.yearlySummaries(emptyList(), LISBON).isEmpty())
    }
}
