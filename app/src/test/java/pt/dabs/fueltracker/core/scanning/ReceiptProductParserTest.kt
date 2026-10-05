package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.dabs.fueltracker.core.domain.model.FuelType
import pt.dabs.fueltracker.core.scanning.ReceiptProductParser.ProductSuggestion

class ReceiptProductParserTest {

    private fun sample(name: String): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("receipts/$name.txt")) { "sample $name missing" }
            .bufferedReader(Charsets.UTF_8).readLines()

    private fun suggest(vararg rows: String) = ReceiptProductParser.suggest(rows.toList())

    @Test
    fun `real Repsol receipt is premium 95`() {
        // "N. de Bomba 3 EFI 95 PREM" — Efitec 95 Premium; o total "95,88" não conta como octanas.
        assertEquals(ProductSuggestion(FuelType.GASOLINE_95, true), ReceiptProductParser.suggest(sample("real_repsol_desconto_imediato")))
    }

    @Test
    fun `samples with explicit products`() {
        assertEquals(ProductSuggestion(FuelType.GASOLINE_95, false), ReceiptProductParser.suggest(sample("galp_linha_unica")))
        assertEquals(ProductSuggestion(FuelType.GASOLINE_98, true), ReceiptProductParser.suggest(sample("bp_tabela"))) // Ultimate 98
        assertEquals(ProductSuggestion(FuelType.DIESEL, false), ReceiptProductParser.suggest(sample("super_com_cafe")))
        assertEquals(ProductSuggestion(FuelType.DIESEL, null), ReceiptProductParser.suggest(sample("so_litros")))
    }

    @Test
    fun `commercial premium names`() {
        assertEquals(true, suggest("GALP G-FORCE GASOLINA 95").premium)
        assertEquals(true, suggest("SHELL V-POWER 95").premium)
        assertEquals(true, suggest("EFITEC 98 NEOTECH").premium)
        assertEquals(FuelType.GASOLINE_98, suggest("EFITEC 98 NEOTECH").type)
        assertEquals(true, suggest("GASOLEO ADITIVADO").premium)
    }

    @Test
    fun `lpg and diesel`() {
        assertEquals(FuelType.LPG, suggest("GPL AUTO 22,10 L").type)
        assertEquals(FuelType.DIESEL, suggest("DIESEL 40,00 L").type)
    }

    @Test
    fun `numbers that are not octanes are ignored`() {
        assertEquals(ProductSuggestion(null, null), suggest("TOTAL 95,88", "IVA 23% 17,93", "A.E.8 KM 13,95-2670-901"))
    }
}
