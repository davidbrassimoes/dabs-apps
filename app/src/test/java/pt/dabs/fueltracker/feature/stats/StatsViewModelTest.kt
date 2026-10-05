package pt.dabs.fueltracker.feature.stats

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pt.dabs.fueltracker.core.domain.LISBON
import pt.dabs.fueltracker.core.domain.at
import pt.dabs.fueltracker.core.domain.fillUp
import pt.dabs.fueltracker.core.domain.model.Period
import pt.dabs.fueltracker.feature.FakeFillUpRepository
import pt.dabs.fueltracker.feature.MainDispatcherRule
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(at("2026-03-10"), LISBON)

    private suspend fun ReceiveTurbine<StatsUiState>.awaitLoaded(): StatsUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `one fill up is not enough data`() = runTest {
        val viewModel = StatsViewModel(FakeFillUpRepository(listOf(fillUp(1, "2026-01-05", 10_000, 40_000))), clock)
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertFalse(state.hasEnoughData)
            assertEquals(1, state.months.size)
        }
    }

    @Test
    fun `state aggregates cycles, months, years, stations and anomalies`() = runTest {
        val fillUps = listOf(
            fillUp(1, "2025-12-20", 10_000, 40_000).copy(stationName = "Galp"),
            fillUp(2, "2026-01-10", 10_600, 36_000).copy(stationName = "Galp"),   // 6,0
            fillUp(3, "2026-01-28", 11_200, 36_000).copy(stationName = "BP"),     // 6,0
            fillUp(4, "2026-02-15", 11_700, 45_000).copy(stationName = "Galp"),   // 9,0 → anomalia
        )
        val viewModel = StatsViewModel(FakeFillUpRepository(fillUps), clock)
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.hasEnoughData)
            assertEquals(listOf(2L, 3L, 4L), state.cycles.map { it.fillUpId })
            assertEquals(listOf(12, 1, 2), state.months.map { (it.period as Period.Month).yearMonth.monthValue })
            assertEquals(listOf(2026, 2025), state.years.map { (it.period as Period.Year).year })
            assertEquals(listOf("galp", "bp"), state.stations.map { it.key })
            assertEquals(1, state.anomalies.size)
            assertEquals(4L, state.anomalies.single().cycle.fillUpId)
            assertEquals(6_000, state.anomalies.single().baselineMlPer100Km)
            assertEquals(6_882, state.overallAverageMlPer100Km) // 117 L / 1700 km
        }
    }
}
