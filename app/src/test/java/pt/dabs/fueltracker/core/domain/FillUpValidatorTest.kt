package pt.dabs.fueltracker.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FillUpValidatorTest {

    private val existing = listOf(
        fillUp(1, "2026-01-01", 10_000, 40_000),
        fillUp(2, "2026-01-20", 10_600, 39_000),
        fillUp(3, "2026-02-10", 11_200, 38_000, atcud = "ABCD1234-5"),
    )

    private fun draft(
        date: String = "2026-03-01",
        odometerKm: Int? = 11_800,
        volumeMl: Int? = 39_000,
        price: Int? = 175_000,
        totalCents: Long? = FuelMath.totalCents(39_000, 175_000),
        id: Long = 0L,
        isFullTank: Boolean = true,
        atcud: String? = null,
        discountCents: Long? = 0,
        tripTenthsKm: Int? = null,
        tripInvalid: Boolean = false,
    ) = FillUpDraft(
        id = id,
        dateTime = at(date),
        odometerKm = odometerKm,
        volumeMl = volumeMl,
        pricePerLiterMilliCents = price,
        totalCents = totalCents,
        isFullTank = isFullTank,
        atcud = atcud,
        discountCents = discountCents,
        tripTenthsKm = tripTenthsKm,
        tripInvalid = tripInvalid,
    )

    @Test
    fun `valid draft has no issues`() {
        assertTrue(FillUpValidator.validate(draft(), existing).isEmpty())
    }

    @Test
    fun `first fill up ever only needs the fields`() {
        assertTrue(FillUpValidator.validate(draft(), emptyList()).isEmpty())
    }

    @Test
    fun `missing fields are errors`() {
        val issues = FillUpValidator.validate(
            draft(odometerKm = null, volumeMl = null, price = null, totalCents = null), existing
        )
        assertEquals(
            setOf(
                ValidationIssue.MissingOdometer, ValidationIssue.MissingVolume,
                ValidationIssue.MissingPrice, ValidationIssue.MissingTotal,
            ),
            issues.toSet()
        )
        assertTrue(issues.hasErrors)
        assertFalse(issues.hasWarnings)
    }

    @Test
    fun `zero or negative values count as missing`() {
        val issues = FillUpValidator.validate(draft(odometerKm = 0, volumeMl = -5, price = 0, totalCents = 0), existing)
        assertEquals(4, issues.count { it.severity == Severity.ERROR })
    }

    @Test
    fun `odometer must be greater than the previous fill up`() {
        val equal = FillUpValidator.validate(draft(odometerKm = 11_200), existing)
        assertTrue(ValidationIssue.OdometerNotAfterPrevious(11_200) in equal)
        val lower = FillUpValidator.validate(draft(odometerKm = 10_000), existing)
        assertTrue(ValidationIssue.OdometerNotAfterPrevious(11_200) in lower)
        assertTrue(lower.hasErrors)
    }

    @Test
    fun `previous is chosen by date not by insertion order`() {
        // rascunho entre o 2 e o 3: o anterior é o 2 (10 600) e o seguinte o 3 (11 200)
        val issues = FillUpValidator.validate(draft(date = "2026-02-01", odometerKm = 10_900, volumeMl = 20_000, price = 170_000, totalCents = FuelMath.totalCents(20_000, 170_000)), existing)
        assertTrue(issues.isEmpty())
        val tooHigh = FillUpValidator.validate(draft(date = "2026-02-01", odometerKm = 11_300), existing)
        assertTrue(ValidationIssue.OdometerNotBeforeNext(11_200) in tooHigh)
    }

    @Test
    fun `editing ignores the record itself`() {
        // editar o 3 mantendo o odómetro: não deve comparar consigo próprio
        val issues = FillUpValidator.validate(
            draft(id = 3, date = "2026-02-10", odometerKm = 11_200, volumeMl = 38_000, price = 170_000, totalCents = FuelMath.totalCents(38_000, 170_000)),
            existing
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `editing the middle record checks both neighbours`() {
        val tooLow = FillUpValidator.validate(draft(id = 2, date = "2026-01-20", odometerKm = 9_999), existing)
        assertTrue(ValidationIssue.OdometerNotAfterPrevious(10_000) in tooLow)
        val tooHigh = FillUpValidator.validate(draft(id = 2, date = "2026-01-20", odometerKm = 11_500), existing)
        assertTrue(ValidationIssue.OdometerNotBeforeNext(11_200) in tooHigh)
    }

    @Test
    fun `implausible consumption is a warning not an error`() {
        // 39 L em 100 km = 39 L/100 km
        val high = FillUpValidator.validate(draft(odometerKm = 11_300), existing)
        assertTrue(high.any { it is ValidationIssue.ImplausibleConsumption })
        assertEquals(39_000, (high.first { it is ValidationIssue.ImplausibleConsumption } as ValidationIssue.ImplausibleConsumption).consumptionMlPer100Km)
        assertFalse(high.hasErrors)
        assertTrue(high.hasWarnings)

        // 39 L em 3000 km = 1,3 L/100 km
        val low = FillUpValidator.validate(draft(odometerKm = 14_200), existing)
        assertTrue(low.any { it is ValidationIssue.ImplausibleConsumption })
    }

    @Test
    fun `plausibility bounds are inclusive`() {
        // 25 L em 100 km = 25,0 → ok; 25,1 → aviso
        val atMax = FillUpValidator.validate(draft(odometerKm = 11_300, volumeMl = 25_000, totalCents = FuelMath.totalCents(25_000, 175_000)), existing)
        assertFalse(atMax.any { it is ValidationIssue.ImplausibleConsumption })
        val aboveMax = FillUpValidator.validate(draft(odometerKm = 11_300, volumeMl = 25_100, totalCents = FuelMath.totalCents(25_100, 175_000)), existing)
        assertTrue(aboveMax.any { it is ValidationIssue.ImplausibleConsumption })
    }

    @Test
    fun `plausibility accounts for partial fill ups in the cycle`() {
        val withPartial = existing + fillUp(4, "2026-02-20", 11_250, 15_000, isFullTank = false)
        // cheio em 11 800: ciclo desde 11 200 = 600 km com 15 + 39 = 54 L → 9 L/100 km, plausível
        val ok = FillUpValidator.validate(draft(odometerKm = 11_800), withPartial)
        assertTrue(ok.isEmpty())
        // cheio em 11 300: 100 km com 54 L → 54 L/100 km
        val bad = FillUpValidator.validate(draft(odometerKm = 11_300), withPartial)
        assertTrue(bad.any { it is ValidationIssue.ImplausibleConsumption })
    }

    @Test
    fun `partial draft does not get a plausibility check`() {
        val issues = FillUpValidator.validate(draft(odometerKm = 11_210, isFullTank = false), existing)
        assertFalse(issues.any { it is ValidationIssue.ImplausibleConsumption })
    }

    @Test
    fun `total mismatch beyond tolerance is a warning`() {
        val expected = FuelMath.totalCents(39_000, 175_000) // 6825
        val within = FillUpValidator.validate(draft(totalCents = expected + 5), existing)
        assertFalse(within.any { it is ValidationIssue.TotalMismatch })
        val beyond = FillUpValidator.validate(draft(totalCents = expected + 6), existing)
        assertTrue(ValidationIssue.TotalMismatch(expected) in beyond)
        assertFalse(beyond.hasErrors)
    }

    @Test
    fun `discount closes the gap between liters times price and the total`() {
        // Talão real: 42,630 L × 2,299 = 98,01 €; desconto 2,13 €; total pago 95,88 €.
        val withDiscount = FillUpValidator.validate(
            draft(volumeMl = 42_630, price = 229_900, totalCents = 9_588, discountCents = 213), emptyList()
        )
        assertTrue(withDiscount.isEmpty())

        val withoutDiscount = FillUpValidator.validate(
            draft(volumeMl = 42_630, price = 229_900, totalCents = 9_588), emptyList()
        )
        assertTrue(ValidationIssue.TotalMismatch(9_801) in withoutDiscount)
    }

    @Test
    fun `invalid or negative discount is an error`() {
        assertTrue(ValidationIssue.InvalidDiscount in FillUpValidator.validate(draft(discountCents = null), existing))
        assertTrue(ValidationIssue.InvalidDiscount in FillUpValidator.validate(draft(discountCents = -1), existing))
    }

    @Test
    fun `trip meter must match the odometer distance since the previous fill up`() {
        // Anterior a 11 200 km; o rascunho a 11 800 km: 600 km pelo conta-km.
        assertTrue(FillUpValidator.validate(draft(tripTenthsKm = 6_000), existing).none { it is ValidationIssue.TripMismatch })
        assertTrue(FillUpValidator.validate(draft(tripTenthsKm = 5_985), existing).none { it is ValidationIssue.TripMismatch })
        assertTrue(ValidationIssue.TripMismatch(3_000, 600) in FillUpValidator.validate(draft(tripTenthsKm = 3_000), existing))
        assertTrue(ValidationIssue.TripMismatch(12_000, 600) in FillUpValidator.validate(draft(tripTenthsKm = 12_000), existing))
        assertFalse(FillUpValidator.validate(draft(tripTenthsKm = 3_000), existing).hasErrors)
        // Sem abastecimento anterior não há com que comparar.
        assertTrue(FillUpValidator.validate(draft(tripTenthsKm = 3_000), emptyList()).isEmpty())
    }

    @Test
    fun `invalid trip text is an error`() {
        assertTrue(ValidationIssue.InvalidTrip in FillUpValidator.validate(draft(tripInvalid = true), existing))
    }

    @Test
    fun `tank capacity check only when configured`() {
        assertFalse(FillUpValidator.validate(draft(volumeMl = 60_000, totalCents = FuelMath.totalCents(60_000, 175_000)), existing).any { it is ValidationIssue.VolumeExceedsTank })
        val issues = FillUpValidator.validate(draft(volumeMl = 60_000, totalCents = FuelMath.totalCents(60_000, 175_000)), existing, tankCapacityMl = 45_000)
        assertTrue(ValidationIssue.VolumeExceedsTank(45_000) in issues)
        val exact = FillUpValidator.validate(draft(volumeMl = 45_000, totalCents = FuelMath.totalCents(45_000, 175_000)), existing, tankCapacityMl = 45_000)
        assertFalse(exact.any { it is ValidationIssue.VolumeExceedsTank })
    }

    @Test
    fun `duplicate atcud is a warning only when flagged and present`() {
        assertTrue(ValidationIssue.DuplicateAtcud in FillUpValidator.validate(draft(atcud = "ABCD1234-5"), existing, atcudAlreadyUsed = true))
        assertFalse(ValidationIssue.DuplicateAtcud in FillUpValidator.validate(draft(atcud = "ABCD1234-5"), existing, atcudAlreadyUsed = false))
        assertFalse(ValidationIssue.DuplicateAtcud in FillUpValidator.validate(draft(atcud = null), existing, atcudAlreadyUsed = true))
        assertFalse(ValidationIssue.DuplicateAtcud in FillUpValidator.validate(draft(atcud = "  "), existing, atcudAlreadyUsed = true))
    }

    @Test
    fun `receipt without nif warns regardless of settings`() {
        val issues = FillUpValidator.validate(draft().copy(buyerNif = Nif.FINAL_CONSUMER), existing)
        assertEquals(listOf(ValidationIssue.ReceiptWithoutNif), issues)
        val withUser = FillUpValidator.validate(draft().copy(buyerNif = Nif.FINAL_CONSUMER), existing, userNif = "123456789")
        assertEquals(listOf(ValidationIssue.ReceiptWithoutNif), withUser)
    }

    @Test
    fun `receipt nif is compared with the configured nif`() {
        val same = FillUpValidator.validate(draft().copy(buyerNif = "123456789"), existing, userNif = "123456789")
        assertTrue(same.isEmpty())
        val different = FillUpValidator.validate(draft().copy(buyerNif = "987654321"), existing, userNif = "123456789")
        assertEquals(listOf(ValidationIssue.ReceiptNifMismatch("987654321")), different)
        // sem NIF configurado não há com que comparar
        val noUser = FillUpValidator.validate(draft().copy(buyerNif = "987654321"), existing)
        assertTrue(noUser.isEmpty())
        // sem leitura de talão não há aviso
        assertTrue(FillUpValidator.validate(draft(), existing, userNif = "123456789").isEmpty())
    }

    @Test
    fun `odometer error suppresses plausibility noise`() {
        val issues = FillUpValidator.validate(draft(odometerKm = 10_000), existing)
        assertTrue(issues.any { it is ValidationIssue.OdometerNotAfterPrevious })
        assertFalse(issues.any { it is ValidationIssue.ImplausibleConsumption })
    }

    @Test
    fun `previousFillUp helper`() {
        assertEquals(3L, FillUpValidator.previousFillUp(existing, at("2026-03-01"))?.id)
        assertEquals(2L, FillUpValidator.previousFillUp(existing, at("2026-02-10"), excludingId = 3)?.id)
        assertEquals(null, FillUpValidator.previousFillUp(existing, at("2025-12-01")))
    }
}
