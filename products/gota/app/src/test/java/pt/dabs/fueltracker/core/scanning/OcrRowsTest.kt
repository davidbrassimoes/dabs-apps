package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrRowsTest {

    @Test
    fun `lines on the same band are merged left to right`() {
        val lines = listOf(
            OcrLine("1,749 €/L", top = 100, bottom = 120, left = 300),
            OcrLine("GASOLINA 95", top = 60, bottom = 80, left = 10),
            OcrLine("40,12 L", top = 102, bottom = 122, left = 20),
            OcrLine("70,17", top = 98, bottom = 118, left = 500),
            OcrLine("TOTAL", top = 140, bottom = 160, left = 10),
        )
        assertEquals(
            listOf("GASOLINA 95", "40,12 L 1,749 €/L 70,17", "TOTAL"),
            OcrRows.merge(lines)
        )
    }

    @Test
    fun `lines further apart than half a line height stay separate`() {
        val lines = listOf(
            OcrLine("LITROS", top = 0, bottom = 20, left = 0),
            OcrLine("28,588", top = 14, bottom = 34, left = 0),
        )
        assertEquals(listOf("LITROS", "28,588"), OcrRows.merge(lines))
    }

    @Test
    fun `blank lines are dropped and empty input is fine`() {
        assertTrue(OcrRows.merge(emptyList()).isEmpty())
        assertEquals(listOf("A"), OcrRows.merge(listOf(OcrLine("  ", 0, 10, 0), OcrLine("A", 0, 10, 5))))
    }
}
