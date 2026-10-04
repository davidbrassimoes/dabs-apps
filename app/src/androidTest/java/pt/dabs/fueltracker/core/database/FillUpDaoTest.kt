package pt.dabs.fueltracker.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FillUpDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: FillUpDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.fillUpDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        id: Long = 0L,
        dateTime: Long,
        odometerKm: Int,
        atcud: String? = null,
    ) = FillUpEntity(
        id = id,
        dateTimeEpochMillis = dateTime,
        odometerKm = odometerKm,
        volumeMl = 40_000,
        pricePerLiterMilliCents = 170_000,
        totalCents = 6_800,
        atcud = atcud,
        createdAtEpochMillis = dateTime,
    )

    @Test
    fun insertAndObserveInChronologicalOrder() = runTest {
        dao.insert(entity(dateTime = 3_000, odometerKm = 300))
        dao.insert(entity(dateTime = 1_000, odometerKm = 100))
        dao.insert(entity(dateTime = 2_000, odometerKm = 200))

        val all = dao.observeAll().first()
        assertEquals(listOf(100, 200, 300), all.map { it.odometerKm })
        assertEquals(3, dao.observeCount().first())
    }

    @Test
    fun updateAndDelete() = runTest {
        val id = dao.insert(entity(dateTime = 1_000, odometerKm = 100))
        val stored = dao.getById(id)!!
        dao.update(stored.copy(odometerKm = 150, stationName = "Galp"))
        assertEquals(150, dao.getById(id)!!.odometerKm)
        assertEquals("Galp", dao.getById(id)!!.stationName)

        dao.delete(dao.getById(id)!!)
        assertNull(dao.getById(id))
    }

    @Test
    fun reinsertKeepsExplicitId() = runTest {
        val id = dao.insert(entity(dateTime = 1_000, odometerKm = 100))
        val stored = dao.getById(id)!!
        dao.delete(stored)
        val restoredId = dao.insert(stored)
        assertEquals(id, restoredId)
        assertEquals(100, dao.getById(id)!!.odometerKm)
    }

    @Test
    fun atcudIsUniqueButNullIsAllowedManyTimes() = runTest {
        dao.insert(entity(dateTime = 1_000, odometerKm = 100, atcud = null))
        dao.insert(entity(dateTime = 2_000, odometerKm = 200, atcud = null))
        val withAtcud = dao.insert(entity(dateTime = 3_000, odometerKm = 300, atcud = "ABCD1234-5"))

        assertTrue(dao.existsAtcud("ABCD1234-5", excludeId = 0L))
        assertFalse(dao.existsAtcud("ABCD1234-5", excludeId = withAtcud))
        assertFalse(dao.existsAtcud("OUTRO-1", excludeId = 0L))

        var failed = false
        try {
            dao.insert(entity(dateTime = 4_000, odometerKm = 400, atcud = "ABCD1234-5"))
        } catch (_: SQLiteConstraintException) {
            failed = true
        }
        assertTrue(failed)
    }
}
