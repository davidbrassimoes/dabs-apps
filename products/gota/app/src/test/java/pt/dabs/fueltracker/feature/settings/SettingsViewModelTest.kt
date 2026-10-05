package pt.dabs.fueltracker.feature.settings

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pt.dabs.fueltracker.core.csv.FillUpCsv
import pt.dabs.fueltracker.core.data.DocumentStore
import pt.dabs.fueltracker.core.domain.LISBON
import pt.dabs.fueltracker.core.domain.at
import pt.dabs.fueltracker.core.domain.fillUp
import pt.dabs.fueltracker.feature.FakeFillUpRepository
import pt.dabs.fueltracker.feature.MainDispatcherRule
import java.io.IOException
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(at("2026-03-10"), LISBON)

    /** Armazém em memória indexado pelo URI em texto. */
    private class FakeDocumentStore : DocumentStore {
        val files = HashMap<String, String>()
        var failNext = false
        override suspend fun writeText(uri: String, text: String) {
            if (failNext) throw IOException("boom")
            files[uri] = text
        }
        override suspend fun readText(uri: String): String {
            if (failNext) throw IOException("boom")
            return files[uri] ?: throw IOException("missing")
        }
    }

    private fun newViewModel(
        fillUps: FakeFillUpRepository = FakeFillUpRepository(),
        documents: FakeDocumentStore = FakeDocumentStore(),
    ) = SettingsViewModel(fillUps, documents, clock)

    @Test
    fun `suggested export file name carries the date`() = runTest {
        assertEquals("fueltracker-2026-03-10.csv", newViewModel().suggestedExportFileName())
    }

    @Test
    fun `export writes all fill ups and reports the count`() = runTest {
        val fillUps = FakeFillUpRepository(listOf(fillUp(1, "2026-01-05", 10_000, 40_000), fillUp(2, "2026-01-25", 10_600, 39_000)))
        val documents = FakeDocumentStore()
        val viewModel = newViewModel(fillUps = fillUps, documents = documents)
        val uri = "content://test/ficheiro.csv"
        viewModel.transferEventFlow.test {
            viewModel.exportTo(uri)
            assertEquals(TransferEvent.Exported(2), awaitItem())
        }
        val parsed = FillUpCsv.parse(documents.files.getValue(uri))
        assertEquals(listOf(10_000, 10_600), parsed.fillUps.map { it.odometerKm })
        assertFalse(viewModel.uiState.value.isTransferring)
    }

    @Test
    fun `import inserts new rows, skips duplicates and counts invalid lines`() = runTest {
        val fillUps = FakeFillUpRepository(listOf(fillUp(1, "2026-01-05", 10_000, 40_000, atcud = "AAAA-1")))
        val documents = FakeDocumentStore()
        val uri = "content://test/ficheiro.csv"
        documents.files[uri] = "data_hora;odometro_km;litros;preco_litro;total_eur;atcud\n" +
            "2026-01-05T11:00:00Z;10000;40;1,70;68,00;AAAA-1\n" +   // repetido pelo ATCUD
            "2026-01-25T11:00:00Z;10600;39;1,70;66,30;\n" +          // novo
            "lixo;10700;39;1,70;66,30;\n"                            // inválido
        val viewModel = newViewModel(fillUps = fillUps, documents = documents)
        viewModel.transferEventFlow.test {
            viewModel.importFrom(uri)
            assertEquals(TransferEvent.Imported(inserted = 1, skippedDuplicates = 1, invalidRows = 1), awaitItem())
        }
        assertEquals(listOf(10_000, 10_600), fillUps.current.map { it.odometerKm }.sorted())
    }

    @Test
    fun `import of an unrecognised file reports the header problem`() = runTest {
        val documents = FakeDocumentStore()
        val uri = "content://test/ficheiro.csv"
        documents.files[uri] = "a;b\n1;2\n"
        val viewModel = newViewModel(documents = documents)
        viewModel.transferEventFlow.test {
            viewModel.importFrom(uri)
            assertTrue(awaitItem() is TransferEvent.HeaderInvalid)
        }
    }

    @Test
    fun `io failures surface as a failed event`() = runTest {
        val documents = FakeDocumentStore().apply { failNext = true }
        val viewModel = newViewModel(documents = documents)
        viewModel.transferEventFlow.test {
            viewModel.exportTo("content://test/x")
            assertEquals(TransferEvent.Failed, awaitItem())
            viewModel.importFrom("content://test/x")
            assertEquals(TransferEvent.Failed, awaitItem())
        }
        assertFalse(viewModel.uiState.value.isTransferring)
    }
}
