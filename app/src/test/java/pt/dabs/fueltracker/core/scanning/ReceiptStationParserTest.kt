package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.dabs.fueltracker.core.domain.model.FuelBrand
import pt.dabs.fueltracker.core.scanning.ReceiptStationParser.StationSuggestion

class ReceiptStationParserTest {

    private fun sample(name: String): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("receipts/$name.txt")) { "sample $name missing" }
            .bufferedReader(Charsets.UTF_8).readLines()

    @Test
    fun `real Repsol receipt gives brand and place, not the operating company`() {
        assertEquals(
            StationSuggestion(FuelBrand.REPSOL, "Loures B-Sentido N/S"),
            ReceiptStationParser.suggest(sample("real_repsol_desconto_imediato")),
        )
    }

    @Test
    fun `brand line with station keywords and code`() {
        assertEquals(StationSuggestion(FuelBrand.GALP, "Odivelas"), ReceiptStationParser.suggest(listOf("PETROGAL, S.A.", "NIF 500697370", "POSTO GALP ODIVELAS", "FATURA")))
        assertEquals(StationSuggestion(FuelBrand.BP, "Amadora"), ReceiptStationParser.suggest(listOf("E.S. BP AMADORA 0231")))
        assertEquals(StationSuggestion(FuelBrand.PRIO, "Setúbal"), ReceiptStationParser.suggest(listOf("PRIO SETÚBAL")))
    }

    @Test
    fun `brand only in the company line gives just the brand`() {
        assertEquals(StationSuggestion(FuelBrand.GALP, ""), ReceiptStationParser.suggest(listOf("PETROGAL S.A.", "RUA TOMAS DA FONSECA", "1600-209 LISBOA")))
    }

    @Test
    fun `old and new names of the same brand map to one entry`() {
        assertEquals(FuelBrand.MOEVE, ReceiptStationParser.suggest(listOf("CEPSA ALVERCA"))?.brand)
        assertEquals(FuelBrand.MOEVE, ReceiptStationParser.suggest(listOf("MOEVE ALVERCA"))?.brand)
    }

    @Test
    fun `supermarket stations and accents`() {
        assertEquals(StationSuggestion(FuelBrand.INTERMARCHE, "Loures"), ReceiptStationParser.suggest(listOf("ITMP ALIMENTAR LDA", "INTERMARCHÉ LOURES")))
        assertEquals(StationSuggestion(FuelBrand.LECLERC, ""), ReceiptStationParser.suggest(listOf("E.LECLERC")))
    }

    @Test
    fun `BP needs to be a word on its own`() {
        assertNull(ReceiptStationParser.suggest(listOf("CAIXA BPI", "TOTAL 45,00")))
    }

    @Test
    fun `receipts without a known brand give nothing`() {
        assertNull(ReceiptStationParser.suggest(sample("galp_linha_unica").filterNot { "GALP" in it.uppercase() }))
        assertNull(ReceiptStationParser.suggest(sample("sem_valores")))
        assertNull(ReceiptStationParser.suggest(emptyList()))
    }

    @Test
    fun `brand codes round trip and unknown codes are null`() {
        FuelBrand.entries.forEach { assertEquals(it, FuelBrand.fromCode(it.code)) }
        assertNull(FuelBrand.fromCode("ESSO"))
        assertNull(FuelBrand.fromCode(null))
    }
}
