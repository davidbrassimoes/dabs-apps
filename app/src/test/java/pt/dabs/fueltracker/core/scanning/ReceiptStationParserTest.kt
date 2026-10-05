package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptStationParserTest {

    private fun sample(name: String): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("receipts/$name.txt")) { "sample $name missing" }
            .bufferedReader(Charsets.UTF_8).readLines()

    @Test
    fun `real Repsol receipt gives brand and place, not the operating company`() {
        assertEquals("Repsol Loures B-Sentido N/S", ReceiptStationParser.suggestName(sample("real_repsol_desconto_imediato")))
    }

    @Test
    fun `brand line with station keywords and code`() {
        assertEquals("Galp Odivelas", ReceiptStationParser.suggestName(listOf("PETROGAL, S.A.", "NIF 500697370", "POSTO GALP ODIVELAS", "FATURA")))
        assertEquals("BP Amadora", ReceiptStationParser.suggestName(listOf("E.S. BP AMADORA 0231")))
        assertEquals("Prio Setúbal", ReceiptStationParser.suggestName(listOf("PRIO SETÚBAL")))
    }

    @Test
    fun `brand only in the company line gives just the brand`() {
        assertEquals("Galp", ReceiptStationParser.suggestName(listOf("PETROGAL S.A.", "RUA TOMAS DA FONSECA", "1600-209 LISBOA")))
    }

    @Test
    fun `supermarket stations and accents`() {
        assertEquals("Intermarché Loures", ReceiptStationParser.suggestName(listOf("ITMP ALIMENTAR LDA", "INTERMARCHÉ LOURES")))
        assertEquals("E.Leclerc", ReceiptStationParser.suggestName(listOf("E.LECLERC")))
    }

    @Test
    fun `BP needs to be a word on its own`() {
        assertNull(ReceiptStationParser.suggestName(listOf("CAIXA BPI", "TOTAL 45,00")))
    }

    @Test
    fun `receipts without a known brand give nothing`() {
        assertNull(ReceiptStationParser.suggestName(sample("galp_linha_unica").filterNot { "GALP" in it.uppercase() }))
        assertNull(ReceiptStationParser.suggestName(sample("sem_valores")))
        assertNull(ReceiptStationParser.suggestName(emptyList()))
    }
}
