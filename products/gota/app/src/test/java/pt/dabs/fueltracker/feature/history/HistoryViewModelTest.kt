package pt.dabs.fueltracker.feature.history

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pt.dabs.fueltracker.core.domain.fillUp
import pt.dabs.fueltracker.feature.FakeFillUpRepository
import pt.dabs.fueltracker.feature.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fillUps = listOf(
        fillUp(1, "2026-01-05", 10_000, 40_000),
        fillUp(2, "2026-01-25", 10_600, 36_000),   // 6,0
        fillUp(3, "2026-02-10", 11_200, 36_000),   // 6,0
        fillUp(4, "2026-02-28", 11_700, 45_000),   // 9,0 → anomalia
    )

    private suspend fun ReceiveTurbine<HistoryUiState>.awaitLoaded(): HistoryUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    @Test
    fun `items are newest first with cycles and anomalies`() = runTest {
        val viewModel = HistoryViewModel(FakeFillUpRepository(fillUps))
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertFalse(state.isLoading)
            assertEquals(listOf(4L, 3L, 2L, 1L), state.items.map { it.fillUp.id })
            assertNull(state.items.last().cycle)
            assertEquals(9_000, state.items.first().cycle?.consumptionMlPer100Km)
            assertTrue(state.items.first().isAnomalous)
            assertFalse(state.items[1].isAnomalous)
        }
    }

    @Test
    fun `delete removes the item and emits an undo event`() = runTest {
        val repository = FakeFillUpRepository(fillUps)
        val viewModel = HistoryViewModel(repository)
        viewModel.eventFlow.test {
            viewModel.delete(fillUps[3])
            val event = awaitItem() as HistoryEvent.Deleted
            assertEquals(4L, event.fillUp.id)
            assertEquals(listOf(1L, 2L, 3L), repository.current.map { it.id })
        }
    }

    @Test
    fun `restore puts the deleted item back with the same id`() = runTest {
        val repository = FakeFillUpRepository(fillUps)
        val viewModel = HistoryViewModel(repository)
        viewModel.uiState.test {
            assertEquals(4, awaitLoaded().items.size)
            viewModel.delete(fillUps[1])
            assertEquals(listOf(4L, 3L, 1L), awaitItem().items.map { it.fillUp.id })
            viewModel.restore(fillUps[1])
            val restored = awaitItem()
            assertEquals(listOf(4L, 3L, 2L, 1L), restored.items.map { it.fillUp.id })
            assertEquals(6_000, restored.items[2].cycle?.consumptionMlPer100Km)
        }
    }
}
