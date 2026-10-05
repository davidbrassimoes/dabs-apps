package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Exemplos retirados textualmente das "Especificações Técnicas – Código QR" da AT (v1.1, out. 2020).
 */
class AtQrCodeParserTest {

    /** Exemplo 1 da especificação: fatura com três espaços fiscais. */
    private val example1 = "A:123456789*B:999999990*C:PT*D:FT*E:N*F:20191231*G:FT AB2019/0035*H:CSDF7T5H-0035" +
        "*I1:PT*I2:12000.00*I3:15000.00*I4:900.00*I5:50000.00*I6:6500.00*I7:80000.00*I8:18400.00" +
        "*J1:PT-AC*J2:10000.00*J3:25000.56*J4:1000.02*J5:75000.00*J6:6750.00*J7:100000.00*J8:18000.00" +
        "*K1:PT-MA*K2:5000.00*K3:12500.00*K4:625.00*K5:25000.00*K6:3000.00*K7:40000.00*K8:8800.00" +
        "*L:100.00*M:25.00*N:64000.02*O:513600.58*P:100.00*Q:kLp0*R:9999*S:TB;PT00000000000000000000000;513500.58"

    /** Exemplo 2 da especificação: fatura simplificada (o caso típico de um talão de combustível). */
    private val example2 = "A:123456789*B:999999990*C:PT*D:FS*E:N*F:20190812*G:FS CDVF/12345*H:CDF7T5HD-12345" +
        "*I1:PT*I7:0.65*I8:0.15*N:0.15*O:0.80*Q:YhGV*R:9999*S:NU;0.80"

    /** Exemplo 3 da especificação: pró-forma com NIF de adquirente. */
    private val example3Prefix = "A:500000000*B:123456789*C:PT*D:PF*E:N*F:20190123*G:PF G2019CB/145789*H:HB6FT7RV-145789" +
        "*I1:PT*I2:12345.34*I3:12532.65*I4:751.96*I5:52789.00*I6:6862.57*I7:32425.69"

    private fun success(raw: String): AtQrCode =
        (AtQrCodeParser.parse(raw) as? AtQrParseResult.Success)?.code
            ?: throw AssertionError("expected success but was ${AtQrCodeParser.parse(raw)}")

    private fun failure(raw: String): AtQrParseResult.Failure =
        AtQrCodeParser.parse(raw) as? AtQrParseResult.Failure
            ?: throw AssertionError("expected failure but was ${AtQrCodeParser.parse(raw)}")

    @Test
    fun `parses the full invoice example`() {
        val code = success(example1)
        assertEquals("123456789", code.issuerNif)
        assertEquals("999999990", code.buyerNif)
        assertTrue(code.buyerIsFinalConsumer)
        assertEquals("PT", code.buyerCountry)
        assertEquals("FT", code.documentType)
        assertEquals("N", code.documentStatus)
        assertEquals(LocalDate.of(2019, 12, 31), code.documentDate)
        assertEquals("FT AB2019/0035", code.documentId)
        assertEquals("CSDF7T5H-0035", code.atcud)
        assertEquals(6_400_002L, code.totalTaxesCents)
        assertEquals(51_360_058L, code.totalCents)
        assertEquals("kLp0", code.hash)
        assertEquals("9999", code.certificate)
        assertEquals("TB;PT00000000000000000000000;513500.58", code.otherInfo)
        assertEquals("PT-AC", code.rawFields["J1"])
        assertEquals(40, code.rawFields.size) // A..H (8) + I/J/K 1..8 (24) + L..S (8)
    }

    @Test
    fun `parses the simplified invoice example`() {
        val code = success(example2)
        assertEquals("FS", code.documentType)
        assertEquals(LocalDate.of(2019, 8, 12), code.documentDate)
        assertEquals("FS CDVF/12345", code.documentId)
        assertEquals("CDF7T5HD-12345", code.atcud)
        assertEquals(80L, code.totalCents)
        assertEquals(15L, code.totalTaxesCents)
        assertEquals("NU;0.80", code.otherInfo)
    }

    @Test
    fun `buyer nif is detected`() {
        val code = success("$example3Prefix*N:7614.53*O:117478.78*Q:abcd*R:9999")
        assertEquals("123456789", code.buyerNif)
        assertFalse(code.buyerIsFinalConsumer)
    }

    @Test
    fun `minimal fields are enough`() {
        val code = success("A:500697256*B:999999990*F:20260304*H:ABCD1234-77*O:52.47")
        assertEquals(5_247L, code.totalCents)
        assertNull(code.documentId)
        assertNull(code.totalTaxesCents)
        assertNull(code.documentType)
    }

    @Test
    fun `tolerates surrounding whitespace and newlines`() {
        val code = success("  A:500697256 * B:999999990*F:20260304*H:ABCD1234-77 *O: 52.47\n")
        assertEquals("500697256", code.issuerNif)
        assertEquals("ABCD1234-77", code.atcud)
        assertEquals(5_247L, code.totalCents)
    }

    @Test
    fun `ignores parts that are not fields`() {
        val code = success("A:500697256*B:999999990*lixo*F:20260304*H:ABCD1234-77*O:52.47*ZZ:1*:semcodigo")
        assertEquals(5, code.rawFields.size)
    }

    @Test
    fun `first occurrence wins for repeated codes`() {
        val code = success("A:500697256*B:999999990*F:20260304*H:ABCD1234-77*O:52.47*O:99.99")
        assertEquals(5_247L, code.totalCents)
    }

    @Test
    fun `amount with comma or without decimals is accepted`() {
        assertEquals(5_247L, success("A:500697256*B:999999990*F:20260304*H:X-1*O:52,47").totalCents)
        assertEquals(5_200L, success("A:500697256*B:999999990*F:20260304*H:X-1*O:52").totalCents)
    }

    @Test
    fun `non AT text is rejected`() {
        assertEquals(AtQrParseResult.Reason.NOT_AT_QR_CODE, failure("https://example.com/promo").reason)
        assertEquals(AtQrParseResult.Reason.NOT_AT_QR_CODE, failure("").reason)
        assertEquals(AtQrParseResult.Reason.NOT_AT_QR_CODE, failure("B:999999990*F:20260304*H:X*O:1.00").reason)
    }

    @Test
    fun `missing required field is reported with its code`() {
        val noTotal = failure("A:500697256*B:999999990*F:20260304*H:ABCD1234-77")
        assertEquals(AtQrParseResult.Reason.MISSING_REQUIRED_FIELD, noTotal.reason)
        assertEquals("O", noTotal.detail)
        val emptyAtcud = failure("A:500697256*B:999999990*F:20260304*H:*O:1.00")
        assertEquals("H", emptyAtcud.detail)
    }

    @Test
    fun `invalid date, amount and nif`() {
        assertEquals(AtQrParseResult.Reason.INVALID_DATE, failure("A:500697256*B:999999990*F:2026-03-04*H:X*O:1.00").reason)
        assertEquals(AtQrParseResult.Reason.INVALID_DATE, failure("A:500697256*B:999999990*F:20261304*H:X*O:1.00").reason)
        assertEquals(AtQrParseResult.Reason.INVALID_AMOUNT, failure("A:500697256*B:999999990*F:20260304*H:X*O:1.000,00").reason)
        assertEquals(AtQrParseResult.Reason.INVALID_AMOUNT, failure("A:500697256*B:999999990*F:20260304*H:X*O:abc").reason)
        assertEquals(AtQrParseResult.Reason.INVALID_NIF, failure("A:12345*B:999999990*F:20260304*H:X*O:1.00").reason)
        assertEquals(AtQrParseResult.Reason.INVALID_NIF, failure("A:PT500697256*B:999999990*F:20260304*H:X*O:1.00").reason)
    }
}
