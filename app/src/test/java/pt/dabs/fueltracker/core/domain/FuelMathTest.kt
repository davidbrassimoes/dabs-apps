package pt.dabs.fueltracker.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FuelMathTest {

    @Test
    fun `roundDiv half up examples`() {
        assertEquals(3L, FuelMath.roundDiv(5, 2))      // 2,5 → 3
        assertEquals(2L, FuelMath.roundDiv(4, 2))      // 2,0 → 2
        assertEquals(1L, FuelMath.roundDiv(14, 10))    // 1,4 → 1
        assertEquals(2L, FuelMath.roundDiv(15, 10))    // 1,5 → 2
        assertEquals(0L, FuelMath.roundDiv(0, 7))
        assertEquals(-3L, FuelMath.roundDiv(-5, 2))    // simétrico
        assertEquals(-1L, FuelMath.roundDiv(-14, 10))
    }

    @Test
    fun `roundDiv rejects non positive divisor`() {
        assertThrows(IllegalArgumentException::class.java) { FuelMath.roundDiv(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { FuelMath.roundDiv(1, -1) }
    }

    @Test
    fun `consumption 40 liters over 600 km is 6,667 L per 100 km`() {
        assertEquals(6_667, FuelMath.consumptionMlPer100Km(40_000, 600))
    }

    @Test
    fun `consumption exact`() {
        assertEquals(5_000, FuelMath.consumptionMlPer100Km(30_000, 600))
    }

    @Test
    fun `consumption rejects zero distance`() {
        assertThrows(IllegalArgumentException::class.java) { FuelMath.consumptionMlPer100Km(1, 0) }
    }

    @Test
    fun `total from volume and price`() {
        // 40,000 L × 1,749 €/L = 69,96 €
        assertEquals(6_996L, FuelMath.totalCents(40_000, 174_900))
        // 35,120 L × 1,699 €/L = 59,66888 € → 59,67 €
        assertEquals(5_967L, FuelMath.totalCents(35_120, 169_900))
        // 0,005 L × 1,000 €/L = 0,005 € → 0,01 € (half up)
        assertEquals(1L, FuelMath.totalCents(5, 100_000))
    }

    @Test
    fun `price from total and volume`() {
        assertEquals(174_900, FuelMath.pricePerLiterMilliCents(6_996, 40_000))
        // 59,67 € / 35,120 L = 1,69903... €/L → 169 903
        assertEquals(169_903, FuelMath.pricePerLiterMilliCents(5_967, 35_120))
    }

    @Test
    fun `volume from total and price`() {
        assertEquals(40_000, FuelMath.volumeMl(6_996, 174_900))
        // 50,00 € / 1,749 €/L = 28,5877... L → 28 588 ml
        assertEquals(28_588, FuelMath.volumeMl(5_000, 174_900))
    }

    @Test
    fun `round trip total price volume stays within tolerance`() {
        val volume = 37_430
        val price = 181_900
        val total = FuelMath.totalCents(volume, price)
        val priceBack = FuelMath.pricePerLiterMilliCents(total, volume)
        val volumeBack = FuelMath.volumeMl(total, price)
        assertEquals(price.toLong(), priceBack.toLong(), 20) // 0,0002 €/L
        assertEquals(volume.toLong(), volumeBack.toLong(), 5) // 5 ml
    }

    @Test
    fun `cost per km`() {
        // 69,96 € / 600 km = 0,1166 €/km → 11 660 milésimos de cêntimo/km
        assertEquals(11_660L, FuelMath.costMilliCentsPerKm(6_996, 600))
    }

    @Test
    fun `average price per liter`() {
        assertEquals(174_900, FuelMath.averagePricePerLiterMilliCents(6_996, 40_000))
        // dois abastecimentos: 40 L a 1,70 + 20 L a 1,60 = 68 + 32 = 100 € / 60 L = 1,66667
        assertEquals(166_667, FuelMath.averagePricePerLiterMilliCents(10_000, 60_000))
    }

    @Test
    fun `range for 45 liter tank at 6,5 L per 100 km`() {
        assertEquals(692, FuelMath.rangeKm(45_000, 6_500))
    }

    private fun assertEquals(expected: Long, actual: Long, tolerance: Long) {
        if (kotlin.math.abs(expected - actual) > tolerance) {
            throw AssertionError("expected $expected ± $tolerance but was $actual")
        }
    }
}
