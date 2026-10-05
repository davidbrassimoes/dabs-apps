package pt.dabs.fueltracker.feature.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import pt.dabs.fueltracker.core.domain.LISBON
import pt.dabs.fueltracker.core.domain.at
import pt.dabs.fueltracker.core.domain.fillUp
import pt.dabs.fueltracker.feature.FakeFillUpRepository
import pt.dabs.fueltracker.feature.MainDispatcherRule
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(at("2026-02-20"), LISBON)

    /** Com o dispatcher Unconfined o estado inicial "a carregar" pode já ter sido substituído. */
    private suspend fun ReceiveTurbine<HomeUiState>.awaitLoaded(): HomeUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    private fun newViewModel(fillUps: FakeFillUpRepository) =
        HomeViewModel(fillUps, clock)

    @Test
    fun `empty repository yields empty state`() = runTest {
        val viewModel = newViewModel(FakeFillUpRepository())
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertFalse(state.isLoading)
            assertEquals(0, state.fillUpCount)
            assertNull(state.lastCycle)
            assertNull(state.recentAverageMlPer100Km)
            assertNull(state.typicalDistancePerTankKm)
            assertEquals(0L, state.thisMonth?.totalCents)
        }
    }

    @Test
    fun `state reflects cycles and current month`() = runTest {
        val repository = FakeFillUpRepository(
            listOf(
                fillUp(1, "2026-01-05", 10_000, 40_000),
                fillUp(2, "2026-01-25", 10_600, 39_000),           // 6,5 L/100
                fillUp(3, "2026-02-15", 11_200, 36_000, 180_000),  // 6,0 L/100, em fevereiro
            )
        )
        val viewModel = newViewModel(repository)
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(3, state.fillUpCount)
            assertEquals(3L, state.lastFillUp?.id)
            assertEquals(6_000, state.lastCycle?.consumptionMlPer100Km)
            assertEquals(6_250, state.recentAverageMlPer100Km) // 75 L / 1200 km
            assertEquals(600, state.thisMonth?.distanceKm)
            assertEquals(36_000L, state.thisMonth?.volumeMl)
            assertEquals(600, state.typicalDistancePerTankKm)
        }
    }

    @Test
    fun `state updates when a fill up is added`() = runTest {
        val repository = FakeFillUpRepository(listOf(fillUp(1, "2026-02-01", 10_000, 40_000)))
        val viewModel = newViewModel(repository)
        viewModel.uiState.test {
            assertNull(awaitLoaded().lastCycle)
            repository.insert(fillUp(0, "2026-02-18", 10_500, 35_000))
            val updated = awaitItem()
            assertEquals(7_000, updated.lastCycle?.consumptionMlPer100Km)
            assertEquals(2, updated.fillUpCount)
        }
    }
}
