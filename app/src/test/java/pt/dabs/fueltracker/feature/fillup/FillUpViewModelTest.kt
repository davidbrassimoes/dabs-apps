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
import pt.dabs.fueltracker.feature.FakeFillUpRepository
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
    private fun newViewModel(repository: FakeFillUpRepository, fillUpId: Long = FillUp.NEW_ID) =
        FillUpViewModel(fillUpId = fillUpId, repository = repository, clock = clock)

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
