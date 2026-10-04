package pt.dabs.fueltracker.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pt.dabs.fueltracker.core.data.UserSettings
import pt.dabs.fueltracker.feature.FakeSettingsRepository
import pt.dabs.fueltracker.feature.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads stored settings as editable text`() = runTest {
        val viewModel = SettingsViewModel(FakeSettingsRepository(UserSettings(nif = "123456789", tankCapacityMl = 45_000)))
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("123456789", state.nifText)
        assertEquals("45", state.tankCapacityText)
        assertTrue(state.canSave)
    }

    @Test
    fun `empty fields are allowed and clear the settings`() = runTest {
        val repository = FakeSettingsRepository(UserSettings(nif = "123456789", tankCapacityMl = 45_000))
        val viewModel = SettingsViewModel(repository)
        viewModel.onNifChange("")
        viewModel.onTankCapacityChange("")
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.saved)
        assertNull(repository.current.nif)
        assertNull(repository.current.tankCapacityMl)
    }

    @Test
    fun `invalid nif blocks saving and is shown after the attempt`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        viewModel.onNifChange("123456780")
        assertEquals(setOf(SettingsError.INVALID_NIF), viewModel.uiState.value.errors)
        assertFalse(viewModel.uiState.value.showErrors)
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.showErrors)
        assertFalse(viewModel.uiState.value.saved)
        assertNull(repository.current.nif)
    }

    @Test
    fun `capacity must be a plausible number of liters`() = runTest {
        val viewModel = SettingsViewModel(FakeSettingsRepository())
        viewModel.onTankCapacityChange("abc")
        assertEquals(setOf(SettingsError.INVALID_CAPACITY), viewModel.uiState.value.errors)
        viewModel.onTankCapacityChange("4500")
        assertEquals(setOf(SettingsError.INVALID_CAPACITY), viewModel.uiState.value.errors)
        viewModel.onTankCapacityChange("0")
        assertEquals(setOf(SettingsError.INVALID_CAPACITY), viewModel.uiState.value.errors)
        viewModel.onTankCapacityChange("45,5")
        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun `valid values are normalized and stored`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        viewModel.onNifChange("500 697 256")
        viewModel.onTankCapacityChange("45")
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.saved)
        assertEquals("500697256", repository.current.nif)
        assertEquals(45_000, repository.current.tankCapacityMl)
    }

    @Test
    fun `editing after save resets the saved flag`() = runTest {
        val viewModel = SettingsViewModel(FakeSettingsRepository())
        viewModel.onTankCapacityChange("45")
        viewModel.onSaveClick()
        assertTrue(viewModel.uiState.value.saved)
        viewModel.onTankCapacityChange("46")
        assertFalse(viewModel.uiState.value.saved)
    }
}
