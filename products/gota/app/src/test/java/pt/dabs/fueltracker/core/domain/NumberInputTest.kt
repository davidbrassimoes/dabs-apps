package pt.dabs.fueltracker.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {

    @Test
    fun `parses comma decimal`() {
        assertEquals(40_500L, NumberInput.parseScaled("40,5", 3))
        assertEquals(40_500L, NumberInput.parseScaled("40,500", 3))
        assertEquals(40_000L, NumberInput.parseScaled("40", 3))
        assertEquals(40_000L, NumberInput.parseScaled("40,", 3))
        assertEquals(500L, NumberInput.parseScaled(",5", 3))
    }

    @Test
    fun `parses dot decimal too`() {
        assertEquals(40_500L, NumberInput.parseScaled("40.5", 3))
    }

    @Test
    fun `ignores spaces including non breaking`() {
        assertEquals(1_234_500L, NumberInput.parseScaled(" 1 234,5 ", 3))
        assertEquals(1_234_500L, NumberInput.parseScaled("1 234,5", 3))
    }

    @Test
    fun `rounds extra decimals half up`() {
        assertEquals(1_235L, NumberInput.parseScaled("1,2345", 3))
        assertEquals(1_234L, NumberInput.parseScaled("1,2344", 3))
        assertEquals(1_235L, NumberInput.parseScaled("1,23451", 3))
        assertEquals(100L, NumberInput.parseScaled("0,999", 2))
    }

    @Test
    fun `scale zero`() {
        assertEquals(12L, NumberInput.parseScaled("12", 0))
        assertEquals(13L, NumberInput.parseScaled("12,5", 0))
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(NumberInput.parseScaled("", 2))
        assertNull(NumberInput.parseScaled("   ", 2))
        assertNull(NumberInput.parseScaled(",", 2))
        assertNull(NumberInput.parseScaled("1,2,3", 2))
        assertNull(NumberInput.parseScaled("1.2,3", 2))
        assertNull(NumberInput.parseScaled("1,2.3", 2))
        assertNull(NumberInput.parseScaled("abc", 2))
        assertNull(NumberInput.parseScaled("1,2a", 2))
        assertNull(NumberInput.parseScaled("-1", 2))
        assertNull(NumberInput.parseScaled("1e3", 2))
        assertNull(NumberInput.parseScaled("1234567890123", 2))
    }

    @Test
    fun `typed helpers use the right scales`() {
        assertEquals(40_500, NumberInput.parseLitersToMl("40,5"))
        assertEquals(174_900, NumberInput.parsePricePerLiterToMilliCents("1,749"))
        assertEquals(170_000, NumberInput.parsePricePerLiterToMilliCents("1,7"))
        assertEquals(6_996L, NumberInput.parseEurosToCents("69,96"))
        assertEquals(7_000L, NumberInput.parseEurosToCents("70"))
    }

    @Test
    fun `odometer accepts digits only`() {
        assertEquals(123_456, NumberInput.parseOdometerKm("123456"))
        assertEquals(123_456, NumberInput.parseOdometerKm("123 456"))
        assertNull(NumberInput.parseOdometerKm(""))
        assertNull(NumberInput.parseOdometerKm("123,4"))
        assertNull(NumberInput.parseOdometerKm("12a"))
        assertNull(NumberInput.parseOdometerKm("12345678"))
    }

    @Test
    fun `formatScaled produces editable text`() {
        assertEquals("40,500", NumberInput.formatScaled(40_500, 3))
        assertEquals("40,5", NumberInput.formatScaled(40_500, 3, trimTrailingZeros = true))
        assertEquals("40", NumberInput.formatScaled(40_000, 3, trimTrailingZeros = true))
        assertEquals("0,05", NumberInput.formatScaled(5, 2))
        assertEquals("0,00", NumberInput.formatScaled(0, 2))
        assertEquals("1,74900", NumberInput.formatScaled(174_900, 5))
        assertEquals("1,749", NumberInput.formatScaled(174_900, 5, trimTrailingZeros = true))
        assertEquals("12", NumberInput.formatScaled(12, 0))
        assertEquals("-0,50", NumberInput.formatScaled(-50, 2))
    }

    @Test
    fun `format and parse round trip`() {
        for (value in listOf(0L, 1L, 999L, 1_000L, 40_500L, 123_456_789L)) {
            assertEquals(value, NumberInput.parseScaled(NumberInput.formatScaled(value, 3), 3))
        }
    }
}
