package pt.dabs.fueltracker.feature.fillup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pt.dabs.fueltracker.core.domain.LISBON
import pt.dabs.fueltracker.core.domain.ValidationIssue
import pt.dabs.fueltracker.core.domain.at
import pt.dabs.fueltracker.core.domain.fillUp
import pt.dabs.fueltracker.core.domain.model.FillUp
import pt.dabs.fueltracker.core.data.UserSettings
import pt.dabs.fueltracker.feature.FakeFillUpRepository
import pt.dabs.fueltracker.feature.FakeSettingsRepository
import pt.dabs.fueltracker.feature.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class FillUpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(at("2026-03-01", "09:30"), LISBON)

    private val existing = listOf(
        fillUp(1, "2026-01-05", 10_000, 40_000),
        fillUp(2, "2026-01-25", 10_600, 39_000),
    )

    // O construtor com SavedStateHandle depende de Bundle (stub nos testes JVM); usa-se o construtor primário.
    private fun newViewModel(
        repository: FakeFillUpRepository,
        fillUpId: Long = FillUp.NEW_ID,
        settings: UserSettings = UserSettings(),
        startWithScan: Boolean = false,
    ) = FillUpViewModel(
        fillUpId = fillUpId,
        startWithScan = startWithScan,
        repository = repository,
        settingsRepository = FakeSettingsRepository(settings),
        clock = clock,
    )

    @Test
    fun `predicted odometer is previous plus the average cycle distance`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        // um ciclo de 600 km → 10 600 + 600
        assertEquals(11_200, viewModel.uiState.value.predictedOdometerKm)
        // ao editar o último registo, a previsão ignora-o
        val editing = newViewModel(FakeFillUpRepository(existing), fillUpId = 2L)
        assertEquals(10_000, editing.uiState.value.predictedOdometerKm)
    }

    @Test
    fun `scanned odometer fills the field and is validated like typed input`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onOdometerScanned(11_250)
        assertEquals("11250", viewModel.uiState.value.odometerText)
        assertFalse(viewModel.uiState.value.issues.any { it is ValidationIssue.OdometerNotAfterPrevious })
        viewModel.onOdometerScanned(10_000)
        assertTrue(viewModel.uiState.value.issues.any { it is ValidationIssue.OdometerNotAfterPrevious })
    }

    @Test
    fun `manual request prefills the predicted odometer only when empty`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onOdometerManualRequested()
        assertEquals("11200", viewModel.uiState.value.odometerText)
        viewModel.onOdometerChange("11300")
        viewModel.onOdometerManualRequested()
        assertEquals("11300", viewModel.uiState.value.odometerText)
    }

    @Test
    fun `quick flow asks for the receipt scan, then the odometer scan, once each`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing), startWithScan = true)
        assertTrue(viewModel.uiState.value.pendingReceiptScan)
        assertFalse(viewModel.uiState.value.pendingOdometerScan)
        viewModel.onReceiptScanStarted()
        assertFalse(viewModel.uiState.value.pendingReceiptScan)

        viewModel.onReceiptScanned(receiptQr, emptyList())
        assertTrue(viewModel.uiState.value.pendingOdometerScan)
        viewModel.onOdometerScanStarted()
        assertFalse(viewModel.uiState.value.pendingOdometerScan)
        viewModel.onOdometerScanned(11_200)
        assertFalse(viewModel.uiState.value.pendingOdometerScan)
    }

    @Test
    fun `no odometer scan is suggested when the odometer is already filled or when editing`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onOdometerChange("11200")
        viewModel.onReceiptScanned(receiptQr, emptyList())
        assertFalse(viewModel.uiState.value.pendingOdometerScan)

        val editing = newViewModel(FakeFillUpRepository(existing), fillUpId = 2L, startWithScan = true)
        assertFalse(editing.uiState.value.pendingReceiptScan)
    }

    private val receiptQr = "A:500697256*B:999999990*C:PT*D:FS*E:N*F:20260301*G:FS 01P2026/4471*H:JFK2H8QX-4471" +
        "*I1:PT*I7:56.88*I8:13.08*N:13.08*O:69.96*Q:Ab3d*R:1234"

    @Test
    fun `scanning an AT qr fills date, total and receipt data`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onDateChange(LocalDate.of(2026, 2, 20))
        viewModel.onQrScanned(receiptQr)
        val state = viewModel.uiState.value
        assertFalse(state.scanFailed)
        assertEquals(LocalDate.of(2026, 3, 1), state.date)
        assertEquals("69,96", state.totalText)
        assertEquals("JFK2H8QX-4471", state.receipt?.atcud)
        assertEquals("500697256", state.receipt?.issuerNif)
        assertEquals("FS 01P2026/4471", state.receipt?.documentId)
        assertTrue(state.issues.any { it is ValidationIssue.ReceiptWithoutNif })
    }

    @Test
    fun `after scanning, typing the volume derives the price from the receipt total`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onQrScanned(receiptQr)
        viewModel.onVolumeChange("40")
        assertEquals("1,749", viewModel.uiState.value.priceText)
    }

    @Test
    fun `saving a scanned receipt stores station nif, atcud and nif flag`() = runTest {
        val repository = FakeFillUpRepository(existing)
        val viewModel = newViewModel(repository)
        viewModel.onQrScanned(receiptQr)
        viewModel.onOdometerChange("11200")
        viewModel.onVolumeChange("40")
        viewModel.onSaveClick()           // aviso de talão sem NIF
        assertTrue(viewModel.uiState.value.askWarningConfirmation)
        viewModel.onConfirmWarnings()
        val saved = repository.current.single { it.id == 3L }
        assertEquals("500697256", saved.stationNif)
        assertEquals("JFK2H8QX-4471", saved.atcud)
        assertEquals(false, saved.buyerNifPresent)
        assertEquals(6_996L, saved.totalCents)
        assertEquals(174_900, saved.pricePerLiterMilliCents)
    }

    @Test
    fun `receipt with the user's nif does not warn, another nif does`() = runTest {
        val withUserNif = receiptQr.replace("B:999999990", "B:123456789")
        val ok = newViewModel(FakeFillUpRepository(existing), settings = UserSettings(nif = "123456789"))
        ok.onQrScanned(withUserNif)
        assertFalse(ok.uiState.value.issues.any { it is ValidationIssue.ReceiptWithoutNif || it is ValidationIssue.ReceiptNifMismatch })

        val other = newViewModel(FakeFillUpRepository(existing), settings = UserSettings(nif = "987654321"))
        other.onQrScanned(withUserNif)
        assertTrue(other.uiState.value.issues.any { it is ValidationIssue.ReceiptNifMismatch })
    }

    @Test
    fun `duplicate atcud warns and is saved without the atcud link`() = runTest {
        val repository = FakeFillUpRepository(existing + fillUp(3, "2026-02-10", 11_000, 30_000, atcud = "JFK2H8QX-4471"))
        val viewModel = newViewModel(repository)
        viewModel.onQrScanned(receiptQr)
        assertTrue(viewModel.uiState.value.issues.any { it is ValidationIssue.DuplicateAtcud })
        viewModel.onOdometerChange("11600")
        viewModel.onVolumeChange("40")
        viewModel.onSaveClick()
        viewModel.onConfirmWarnings()
        val saved = repository.current.single { it.id == 4L }
        assertNull(saved.atcud)
        assertEquals("500697256", saved.stationNif)
    }

    @Test
    fun `ocr rows fill liters and price when they match the qr total`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onReceiptScanned(receiptQr, listOf("GASOLINA 95 SIMPLES", "40,00 L x 1,749 EUR/L", "TOTAL 69,96"))
        val state = viewModel.uiState.value
        assertEquals("40", state.volumeText)
        assertEquals("1,749", state.priceText)
        assertEquals("69,96", state.totalText)
        assertEquals(OcrOutcome.VOLUME_AND_PRICE, state.ocrOutcome)
        assertFalse(state.issues.any { it is ValidationIssue.TotalMismatch })
    }

    @Test
    fun `ocr with liters only derives the price from the receipt total`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onReceiptScanned(receiptQr, listOf("GASOLEO", "QUANTIDADE 40,00 L", "TOTAL 69,96"))
        val state = viewModel.uiState.value
        assertEquals("40", state.volumeText)
        assertEquals("1,749", state.priceText)
        assertEquals(OcrOutcome.VOLUME_ONLY, state.ocrOutcome)
    }

    @Test
    fun `ocr that reads nothing leaves liters and price empty`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onReceiptScanned(receiptQr, listOf("OBRIGADO", "VOLTE SEMPRE"))
        val state = viewModel.uiState.value
        assertEquals("", state.volumeText)
        assertEquals("", state.priceText)
        assertEquals("69,96", state.totalText)
        assertEquals(OcrOutcome.NOTHING, state.ocrOutcome)
        // escrever os litros a seguir continua a derivar o preço do total
        viewModel.onVolumeChange("40")
        assertEquals("1,749", viewModel.uiState.value.priceText)
    }

    @Test
    fun `ocr values inconsistent with the total are kept and the mismatch warns`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        // talão com café: litros × preço = 65,61 mas o total do QR é 69,96
        viewModel.onReceiptScanned(receiptQr, listOf("GASOLEO SIMPLES", "41,03 LT 1,599 €/L 65,61", "CAFE 4,35", "TOTAL 69,96"))
        val state = viewModel.uiState.value
        assertEquals("41,03", state.volumeText)
        assertEquals("1,599", state.priceText)
        assertEquals("69,96", state.totalText)
        assertTrue(state.issues.any { it is ValidationIssue.TotalMismatch })
    }

    @Test
    fun `a second scan replaces what the first one filled in`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onReceiptScanned(receiptQr, listOf("40,00 L x 1,749 EUR/L"))
        val second = receiptQr.replace("O:69.96", "O:52.47").replace("H:JFK2H8QX-4471", "H:JFK2H8QX-4472")
        viewModel.onReceiptScanned(second, listOf("30,00 L x 1,749 EUR/L"))
        val state = viewModel.uiState.value
        assertEquals("30", state.volumeText)
        assertEquals("1,749", state.priceText)
        assertEquals("52,47", state.totalText)
        assertEquals("JFK2H8QX-4472", state.receipt?.atcud)
    }

    @Test
    fun `non AT qr sets the scan error and leaves the form untouched`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onTotalChange("10")
        viewModel.onQrScanned("https://example.com")
        val state = viewModel.uiState.value
        assertTrue(state.scanFailed)
        assertNull(state.receipt)
        assertEquals("10", state.totalText)
        viewModel.onDismissScanError()
        assertFalse(viewModel.uiState.value.scanFailed)
    }

    @Test
    fun `tank capacity from settings produces a warning when exceeded`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing), settings = UserSettings(tankCapacityMl = 45_000))
        viewModel.onOdometerChange("11200")
        viewModel.onVolumeChange("50")
        viewModel.onPriceChange("1,749")
        assertTrue(viewModel.uiState.value.issues.any { it is ValidationIssue.VolumeExceedsTank })
        assertTrue(viewModel.uiState.value.canSave) // aviso, não erro
        viewModel.onVolumeChange("44")
        assertFalse(viewModel.uiState.value.issues.any { it is ValidationIssue.VolumeExceedsTank })
    }

    @Test
    fun `new fill up starts with now and previous odometer hint`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isEdit)
        assertEquals(LocalDate.of(2026, 3, 1), state.date)
        assertEquals(LocalTime.of(9, 30), state.time)
        assertEquals(10_600, state.previousOdometerKm)
        assertTrue(state.isFullTank)
        assertFalse(state.canSave) // campos em falta
        assertFalse(state.showErrors)
    }

    @Test
    fun `total is derived from volume and price`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onVolumeChange("40")
        viewModel.onPriceChange("1,749")
        assertEquals("69,96", viewModel.uiState.value.totalText)
        viewModel.onVolumeChange("30")
        assertEquals("52,47", viewModel.uiState.value.totalText)
    }

    @Test
    fun `price is derived from total and volume`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onTotalChange("50")
        viewModel.onVolumeChange("28,588")
        assertEquals("1,749", viewModel.uiState.value.priceText)
    }

    @Test
    fun `volume is derived from total and price`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onTotalChange("50")
        viewModel.onPriceChange("1,749")
        assertEquals("28,588", viewModel.uiState.value.volumeText)
    }

    @Test
    fun `once all three were typed nothing is derived and mismatch warns`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onVolumeChange("40")
        viewModel.onPriceChange("1,749")
        viewModel.onTotalChange("75")
        viewModel.onVolumeChange("41")
        assertEquals("75", viewModel.uiState.value.totalText)
        assertTrue(viewModel.uiState.value.issues.any { it is ValidationIssue.TotalMismatch })
    }

    @Test
    fun `derived field is cleared when inputs become invalid`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onVolumeChange("40")
        viewModel.onPriceChange("1,749")
        viewModel.onPriceChange("")
        assertEquals("", viewModel.uiState.value.totalText)
    }

    @Test
    fun `save with errors shows them and does not persist`() = runTest {
        val repository = FakeFillUpRepository(existing)
        val viewModel = newViewModel(repository)
        viewModel.onOdometerChange("10500") // abaixo do anterior
        viewModel.onVolumeChange("40")
        viewModel.onPriceChange("1,749")
        viewModel.onSaveClick()
        val state = viewModel.uiState.value
        assertTrue(state.showErrors)
        assertFalse(state.saved)
        assertTrue(state.issues.any { it is ValidationIssue.OdometerNotAfterPrevious })
        assertEquals(2, repository.current.size)
    }

    @Test
    fun `valid fill up is saved with derived total`() = runTest {
        val repository = FakeFillUpRepository(existing)
        val viewModel = newViewModel(repository)
        viewModel.onOdometerChange("11200")
        viewModel.onVolumeChange("39,5")
        viewModel.onPriceChange("1,699")
        viewModel.onStationNameChange("  Galp A1  ")
        viewModel.onNotesChange("")
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.saved)

        val saved = repository.current.single { it.id == 3L }
        assertEquals(11_200, saved.odometerKm)
        assertEquals(39_500, saved.volumeMl)
        assertEquals(169_900, saved.pricePerLiterMilliCents)
        assertEquals(6_711L, saved.totalCents) // 39,5 × 1,699 = 67,1105
        assertEquals("Galp A1", saved.stationName)
        assertNull(saved.notes)
        assertTrue(saved.isFullTank)
        assertEquals(at("2026-03-01", "09:30"), saved.dateTime)
        assertEquals(clock.instant(), saved.createdAt)
    }

    @Test
    fun `warnings require confirmation before saving`() = runTest {
        val repository = FakeFillUpRepository(existing)
        val viewModel = newViewModel(repository)
        viewModel.onOdometerChange("10700") // 100 km com 40 L → 40 L/100 km
        viewModel.onVolumeChange("40")
        viewModel.onPriceChange("1,749")
        viewModel.onSaveClick()
        var state = viewModel.uiState.value
        assertTrue(state.askWarningConfirmation)
        assertFalse(state.saved)
        assertEquals(2, repository.current.size)

        viewModel.onDismissWarnings()
        assertFalse(viewModel.uiState.value.askWarningConfirmation)

        viewModel.onSaveClick()
        viewModel.onConfirmWarnings()
        state = viewModel.uiState.value
        assertTrue(state.saved)
        assertEquals(3, repository.current.size)
    }

    @Test
    fun `editing loads the record and updates it in place`() = runTest {
        val repository = FakeFillUpRepository(existing)
        val viewModel = newViewModel(repository, fillUpId = 2L)
        val loaded = viewModel.uiState.value
        assertTrue(loaded.isEdit)
        assertEquals("10600", loaded.odometerText)
        assertEquals("39", loaded.volumeText)
        assertEquals("1,7", loaded.priceText)
        assertEquals("66,30", loaded.totalText)
        assertEquals(LocalDate.of(2026, 1, 25), loaded.date)
        assertEquals(10_000, loaded.previousOdometerKm)
        assertTrue(loaded.issues.isEmpty())

        viewModel.onOdometerChange("10650")
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.saved)
        assertEquals(2, repository.current.size)
        val updated = repository.current.single { it.id == 2L }
        assertEquals(10_650, updated.odometerKm)
        assertEquals(existing[1].createdAt, updated.createdAt)
    }

    @Test
    fun `editing an existing record does not derive amounts`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing), fillUpId = 2L)
        viewModel.onVolumeChange("50")
        assertEquals("66,30", viewModel.uiState.value.totalText)
        assertNotNull(viewModel.uiState.value.issues.firstOrNull { it is ValidationIssue.TotalMismatch })
    }

    @Test
    fun `changing the date re-evaluates the previous fill up`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository(existing))
        viewModel.onDateChange(LocalDate.of(2026, 1, 10))
        assertEquals(10_000, viewModel.uiState.value.previousOdometerKm)
        viewModel.onOdometerChange("10700")
        assertTrue(viewModel.uiState.value.issues.any { it is ValidationIssue.OdometerNotBeforeNext })
    }
}
