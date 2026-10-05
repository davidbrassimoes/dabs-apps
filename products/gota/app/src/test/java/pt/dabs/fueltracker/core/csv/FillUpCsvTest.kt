package pt.dabs.fueltracker.core.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.dabs.fueltracker.core.domain.at
import pt.dabs.fueltracker.core.domain.fillUp
import pt.dabs.fueltracker.core.domain.model.FillUp
import pt.dabs.fueltracker.core.domain.model.FuelBrand
import pt.dabs.fueltracker.core.domain.model.FuelType

class FillUpCsvTest {

    private val sample = listOf(
        fillUp(1, "2026-01-05", 10_000, 40_120, 174_900).copy(
            totalCents = 6_804, discountCents = 213, tripTenthsKm = 6_020,
            brand = FuelBrand.GALP, fuelType = FuelType.GASOLINE_95, fuelPremium = false,
            stationNif = "500697256", stationName = "Galp A1", atcud = "JFK2H8QX-4471", buyerNifPresent = false,
            notes = "cheio; \"primeiro\" talão\ncom duas linhas",
        ),
        fillUp(2, "2026-01-25", 10_600, 39_000, 169_900, isFullTank = false),
    )

    @Test
    fun `export writes header and one quoted row per fill up`() {
        val csv = FillUpCsv.export(sample)
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertEquals(FillUpCsv.HEADER.joinToString(";"), lines[0])
        assertTrue(csv.startsWith("﻿"))
        assertTrue(lines[1].startsWith("1;" + at("2026-01-05") + ";10000;602,0;40,120;1,74900;68,04;2,13;1;500697256;Galp A1;GALP;GASOLINA_95;0;JFK2H8QX-4471;0;\"cheio; \"\"primeiro\"\" talão"))
        assertTrue(csv.contains("2;" + at("2026-01-25") + ";10600;;39,000;1,69900;66,26;0,00;0;;;;;;"))
    }

    @Test
    fun `export then parse round trips every field`() {
        val result = FillUpCsv.parse(FillUpCsv.export(sample))
        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.fillUps.size)
        val first = result.fillUps[0]
        assertEquals(FillUp.NEW_ID, first.id) // ids nunca são importados
        assertEquals(sample[0].copy(id = FillUp.NEW_ID), first)
        assertEquals(sample[1].copy(id = FillUp.NEW_ID), result.fillUps[1])
    }

    @Test
    fun `parse accepts dot decimals, LF endings, no BOM and reordered columns`() {
        val csv = "litros;data_hora;odometro_km;preco_litro;total_eur\n" +
            "40.12;2026-03-01T12:00:00Z;11200;1.749;70.17\n"
        val result = FillUpCsv.parse(csv)
        assertTrue(result.errors.isEmpty())
        val fillUp = result.fillUps.single()
        assertEquals(40_120, fillUp.volumeMl)
        assertEquals(174_900, fillUp.pricePerLiterMilliCents)
        assertEquals(7_017L, fillUp.totalCents)
        assertEquals(0L, fillUp.discountCents) // ficheiros sem a coluna desconto_eur
        assertNull(fillUp.tripTenthsKm) // nem a coluna parcial_km
        assertEquals(11_200, fillUp.odometerKm)
        assertTrue(fillUp.isFullTank)
        assertNull(fillUp.atcud)
        assertEquals(fillUp.dateTime, fillUp.createdAt)
    }

    @Test
    fun `invalid or negative discount is a row error`() {
        val csv = "data_hora;odometro_km;litros;preco_litro;total_eur;desconto_eur\n" +
            "2026-03-01T12:00:00Z;11200;40,12;1,749;68,04;abc\n" +
            "2026-03-02T12:00:00Z;11300;40,12;1,749;68,04;-2,13\n"
        val result = FillUpCsv.parse(csv)
        assertTrue(result.fillUps.isEmpty())
        assertEquals(listOf(2, 3), result.errors.map { it.line })
    }

    @Test
    fun `parse accepts offset date times and boolean spellings`() {
        val csv = "data_hora;odometro_km;litros;preco_litro;total_eur;deposito_cheio;fatura_com_nif\n" +
            "2026-03-01T13:00:00+01:00;11200;40;1,749;69,96;não;sim\n"
        val fillUp = FillUpCsv.parse(csv).fillUps.single()
        assertEquals(java.time.Instant.parse("2026-03-01T12:00:00Z"), fillUp.dateTime)
        assertFalse(fillUp.isFullTank)
        assertEquals(true, fillUp.buyerNifPresent)
    }

    @Test
    fun `bad rows are reported with their line and the rest is kept`() {
        val csv = "data_hora;odometro_km;litros;preco_litro;total_eur\r\n" +
            "2026-03-01T12:00:00Z;11200;40;1,749;69,96\r\n" +
            "ontem;11300;40;1,749;69,96\r\n" +
            "2026-03-02T12:00:00Z;abc;40;1,749;69,96\r\n" +
            "2026-03-03T12:00:00Z;11400;0;1,749;69,96\r\n" +
            "\r\n" +
            "2026-03-04T12:00:00Z;11500;30;1,749;52,47\r\n"
        val result = FillUpCsv.parse(csv)
        assertEquals(listOf(11_200, 11_500), result.fillUps.map { it.odometerKm })
        assertEquals(listOf(3, 4, 5), result.errors.map { it.line })
        assertTrue(result.errors[0].reason.contains("data_hora"))
        assertTrue(result.errors[1].reason.contains("odometro_km"))
        assertTrue(result.errors[2].reason.contains("litros"))
    }

    @Test
    fun `missing required columns or empty file fail at the header`() {
        val noColumns = FillUpCsv.parse("id;notas\n1;x\n")
        assertTrue(noColumns.fillUps.isEmpty())
        assertEquals(1, noColumns.errors.single().line)
        assertTrue(noColumns.errors.single().reason.contains("litros"))
        assertEquals(1, FillUpCsv.parse("").errors.size)
        assertEquals(1, FillUpCsv.parse("﻿").errors.size)
    }

    @Test
    fun `import plan skips rows already stored by atcud or by date and odometer`() {
        val existing = listOf(
            fillUp(1, "2026-01-05", 10_000, 40_000, atcud = "AAAA-1"),
            fillUp(2, "2026-01-25", 10_600, 39_000),
        )
        val parsed = listOf(
            fillUp(0, "2026-02-10", 11_100, 38_000, atcud = "AAAA-1"),  // mesmo ATCUD
            fillUp(0, "2026-01-25", 10_600, 10_000),                    // mesma data e odómetro
            fillUp(0, "2026-02-15", 11_300, 36_000, atcud = "BBBB-2"),  // novo
            fillUp(0, "2026-02-15", 11_300, 36_000),                    // repetido dentro do ficheiro
            fillUp(0, "2026-02-28", 11_900, 35_000, atcud = "BBBB-2"),  // ATCUD repetido dentro do ficheiro
        )
        val plan = FillUpCsv.planImport(existing, parsed)
        assertEquals(listOf(11_300), plan.toInsert.map { it.odometerKm })
        assertEquals(4, plan.skippedDuplicates)
    }

    @Test
    fun `import plan with nothing stored inserts everything distinct`() {
        val parsed = listOf(fillUp(0, "2026-01-05", 10_000, 40_000), fillUp(0, "2026-01-25", 10_600, 39_000))
        val plan = FillUpCsv.planImport(emptyList(), parsed)
        assertEquals(2, plan.toInsert.size)
        assertEquals(0, plan.skippedDuplicates)
    }
}
