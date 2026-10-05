package pt.dabs.fueltracker.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class PtFormatTest {

    private val nbsp = " "

    @Test
    fun euros() {
        assertEquals("69,96${nbsp}€", PtFormat.euros(6_996))
        assertEquals("0,05${nbsp}€", PtFormat.euros(5))
        assertEquals("1${grouping()}234,50${nbsp}€", PtFormat.euros(123_450))
    }

    @Test
    fun liters() {
        assertEquals("40,50${nbsp}L", PtFormat.liters(40_500))
        assertEquals("40,00${nbsp}L", PtFormat.liters(40_000))
    }

    @Test
    fun pricePerLiter() {
        assertEquals("1,749${nbsp}€/L", PtFormat.pricePerLiter(174_900))
    }

    @Test
    fun consumption() {
        assertEquals("6,7${nbsp}L/100${nbsp}km", PtFormat.consumption(6_670))
        assertEquals("6,5${nbsp}L/100${nbsp}km", PtFormat.consumption(6_500))
        assertEquals("6,67", PtFormat.consumptionPrecise(6_670))
    }

    @Test
    fun km() {
        assertEquals("123${grouping()}456${nbsp}km", PtFormat.km(123_456))
        assertEquals("600${nbsp}km", PtFormat.km(600))
    }

    @Test
    fun costPerKm() {
        assertEquals("0,117${nbsp}€/km", PtFormat.costPerKm(11_700))
    }

    @Test
    fun dates() {
        val zone = ZoneId.of("Europe/Lisbon")
        val instant = Instant.parse("2026-03-05T14:07:00Z")
        assertEquals("5 mar 2026", PtFormat.date(instant, zone).lowercase().replace(".", ""))
        assertEquals("5 mar 2026, 14:07", PtFormat.dateTime(instant, zone).lowercase().replace(".", ""))
        assertEquals("Março 2026", PtFormat.month(YearMonth.of(2026, 3)))
    }

    /** O separador de milhares de pt-PT no JDK é um espaço (normal ou fino). */
    private fun grouping(): String {
        val symbol = java.text.DecimalFormatSymbols(PtFormat.locale).groupingSeparator
        return symbol.toString()
    }
}
