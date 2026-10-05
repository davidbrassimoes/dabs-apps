package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As amostras em `src/test/resources/receipts` são textos com os layouts habituais dos talões
 * portugueses. As `real_*` são o texto que o ML Kit devolveu para fotografias reais (já fundido
 * por linha com [OcrRows]), anonimizado; as restantes são sintéticas.
 */
class ReceiptTextParserTest {

    private fun sample(name: String): List<String> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("receipts/$name.txt")) { "sample $name missing" }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
    }

    @Test
    fun `real Repsol receipt with immediate discount is confirmed by total plus discount`() {
        // E 42,630 Litros a 2,299 e/Ltr 98,01 € · Desconto Imediato -2,13 · Total 95,88 (= O do QR)
        val result = ReceiptTextParser.parse(sample("real_repsol_desconto_imediato"), totalCents = 9_588)
        assertEquals(42_630, result.volumeMl)
        assertEquals(229_900, result.pricePerLiterMilliCents)
        assertEquals(AmountSource.OCR, result.volumeSource)
        assertEquals(AmountSource.OCR, result.priceSource)
        assertEquals(true, result.consistentWithTotal)
        assertEquals(213L, result.discountCents)
        assertEquals(9_588L, result.printedTotalCents)
    }

    @Test
    fun `real Repsol receipt without qr uses the printed total`() {
        val result = ReceiptTextParser.parse(sample("real_repsol_desconto_imediato"), totalCents = null)
        assertEquals(42_630, result.volumeMl)
        assertEquals(229_900, result.pricePerLiterMilliCents)
        assertEquals(true, result.consistentWithTotal)
        assertEquals(213L, result.discountCents)
        assertEquals(9_588L, result.printedTotalCents)
    }

    @Test
    fun `qr total before discount is also accepted`() {
        // Se um posto codificar no QR o total antes do desconto, o par continua confirmado.
        val result = ReceiptTextParser.parse(sample("real_repsol_desconto_imediato"), totalCents = 9_801)
        assertEquals(42_630, result.volumeMl)
        assertEquals(229_900, result.pricePerLiterMilliCents)
        assertEquals(true, result.consistentWithTotal)
    }

    @Test
    fun `tax tables never provide the price`() {
        // ISP: taxa × quantidade = valor tem o mesmo padrão da linha do combustível.
        val rows = listOf("Tipo ISP Taxa Qt Total", "ISP 0 0,583 €/Lt 42,63 24,85 €")
        val result = ReceiptTextParser.parse(rows, totalCents = 9_588)
        assertNull(result.pricePerLiterMilliCents)
    }

    @Test
    fun `per litre discount uses the discount amount, not the rate`() {
        val result = ReceiptTextParser.parse(sample("galp_desconto_por_litro"), totalCents = 6_800)
        assertEquals(40_000, result.volumeMl)
        assertEquals(180_000, result.pricePerLiterMilliCents)
        assertEquals(400L, result.discountCents)
        assertEquals(true, result.consistentWithTotal)
    }

    @Test
    fun `only liters with a discount derives the price from the gross amount`() {
        val rows = listOf("GASOLINA 95", "LITROS 40,00", "DESCONTO -4,00", "TOTAL 68,00")
        val result = ReceiptTextParser.parse(rows, totalCents = 6_800)
        assertEquals(40_000, result.volumeMl)
        assertEquals(180_000, result.pricePerLiterMilliCents)
        assertEquals(AmountSource.DERIVED, result.priceSource)
    }

    @Test
    fun `euro sign read as e still marks a price`() {
        val result = ReceiptTextParser.parse(listOf("GASOLINA 95", "40,12 L 1,749 e/Ltr"), totalCents = null)
        assertEquals(40_120, result.volumeMl)
        assertEquals(174_900, result.pricePerLiterMilliCents)
    }

    @Test
    fun `single line with liters x price confirmed by the qr total`() {
        val result = ReceiptTextParser.parse(sample("galp_linha_unica"), totalCents = 7_017)
        assertEquals(40_120, result.volumeMl)
        assertEquals(174_900, result.pricePerLiterMilliCents)
        assertEquals(AmountSource.OCR, result.volumeSource)
        assertEquals(AmountSource.OCR, result.priceSource)
        assertEquals(true, result.consistentWithTotal)
    }

    @Test
    fun `table layout with quantity, price and value columns`() {
        val result = ReceiptTextParser.parse(sample("bp_tabela"), totalCents = 6_741)
        assertEquals(35_500, result.volumeMl)
        assertEquals(189_900, result.pricePerLiterMilliCents)
        assertEquals(true, result.consistentWithTotal)
    }

    @Test
    fun `liters and price on separate labelled lines`() {
        val result = ReceiptTextParser.parse(sample("repsol_linhas_separadas"), totalCents = 5_000)
        assertEquals(28_588, result.volumeMl)
        assertEquals(174_900, result.pricePerLiterMilliCents)
        assertEquals(true, result.consistentWithTotal)
    }

    @Test
    fun `receipt with another item keeps both values and flags the total`() {
        val result = ReceiptTextParser.parse(sample("super_com_cafe"), totalCents = 6_641)
        assertEquals(41_030, result.volumeMl)
        assertEquals(159_900, result.pricePerLiterMilliCents)
        assertEquals(AmountSource.OCR, result.volumeSource)
        assertEquals(AmountSource.OCR, result.priceSource)
        assertEquals(false, result.consistentWithTotal)
    }

    @Test
    fun `ocr confusions between O and 0, l and 1 are repaired`() {
        val result = ReceiptTextParser.parse(sample("ocr_ruido"), totalCents = 7_017)
        assertEquals(40_120, result.volumeMl)
        assertEquals(174_900, result.pricePerLiterMilliCents)
        assertEquals(true, result.consistentWithTotal)
    }

    @Test
    fun `only liters printed derives the price from the total`() {
        val result = ReceiptTextParser.parse(sample("so_litros"), totalCents = 6_700)
        assertEquals(38_200, result.volumeMl)
        assertEquals(AmountSource.OCR, result.volumeSource)
        // 67,00 / 38,20 = 1,75392... → 1,754 €/L
        assertEquals(175_400, result.pricePerLiterMilliCents)
        assertEquals(AmountSource.DERIVED, result.priceSource)
    }

    @Test
    fun `only price printed derives the liters from the total`() {
        val result = ReceiptTextParser.parse(sample("so_preco"), totalCents = 6_000)
        assertEquals(169_900, result.pricePerLiterMilliCents)
        assertEquals(AmountSource.OCR, result.priceSource)
        // 60,00 / 1,699 = 35,315 L
        assertEquals(35_315, result.volumeMl)
        assertEquals(AmountSource.DERIVED, result.volumeSource)
    }

    @Test
    fun `price before volume on different lines`() {
        val result = ReceiptTextParser.parse(sample("colunas_trocadas"), totalCents = 7_125)
        assertEquals(44_010, result.volumeMl)
        assertEquals(161_900, result.pricePerLiterMilliCents)
        assertEquals(true, result.consistentWithTotal)
    }

    @Test
    fun `nothing usable yields empty`() {
        assertEquals(ReceiptAmounts.EMPTY, ReceiptTextParser.parse(sample("sem_valores"), totalCents = 7_017))
        assertEquals(ReceiptAmounts.EMPTY, ReceiptTextParser.parse(emptyList(), totalCents = 7_017))
    }

    @Test
    fun `without total, keywords alone are needed`() {
        val withKeywords = ReceiptTextParser.parse(listOf("GASOLINA 95", "40,12 L", "1,749 €/L"), totalCents = null)
        assertEquals(40_120, withKeywords.volumeMl)
        assertEquals(174_900, withKeywords.pricePerLiterMilliCents)
        assertNull(withKeywords.consistentWithTotal)

        val bareNumbers = ReceiptTextParser.parse(listOf("40,12", "1,749"), totalCents = null)
        assertNull(bareNumbers.volumeMl)
        // 1,749 com 3 decimais ainda pontua como preço (2 pontos)
        assertEquals(174_900, bareNumbers.pricePerLiterMilliCents)
    }

    @Test
    fun `total itself is never taken as liters or price`() {
        val result = ReceiptTextParser.parse(listOf("TOTAL 1,80", "GASOLINA 1,80 L"), totalCents = 180)
        // 1,80 L × preço desconhecido: sem preço plausível nada é derivado (1,80 € / 1,80 L = 1,000 €/L é plausível)
        assertEquals(1_800, result.volumeMl)
        assertEquals(100_000, result.pricePerLiterMilliCents)
        assertEquals(AmountSource.DERIVED, result.priceSource)
    }

    @Test
    fun `money marked numbers are not liters`() {
        val result = ReceiptTextParser.parse(listOf("GASOLINA 95 SIMPLES", "€ 45,00", "TOTAL 45,00"), totalCents = 4_500)
        assertNull(result.volumeMl)
        assertNull(result.pricePerLiterMilliCents)
    }

    @Test
    fun `dates, times and percentages are ignored`() {
        val result = ReceiptTextParser.parse(listOf("01/03/2026 14:07", "IVA 23%", "NIF 500697256"), totalCents = 7_017)
        assertEquals(ReceiptAmounts.EMPTY, result)
    }

    @Test
    fun `implausible values are discarded`() {
        val result = ReceiptTextParser.parse(listOf("LITROS 950,00", "PREÇO 9,999 €/L"), totalCents = null)
        assertNull(result.volumeMl)
        assertNull(result.pricePerLiterMilliCents)
        assertFalse(result.consistentWithTotal == true)
    }

    @Test
    fun `tolerance grows slightly with the total`() {
        // 40,12 × 1,749 = 70,17; total do QR 70,20 (3 cêntimos + 7 de 0,1 %) ainda confirma
        val ok = ReceiptTextParser.parse(listOf("40,12 L 1,749 €/L"), totalCents = 7_020)
        assertEquals(true, ok.consistentWithTotal)
        val off = ReceiptTextParser.parse(listOf("40,12 L 1,749 €/L"), totalCents = 7_100)
        assertEquals(false, off.consistentWithTotal)
        assertTrue(off.volumeMl == 40_120 && off.pricePerLiterMilliCents == 174_900)
    }
}
