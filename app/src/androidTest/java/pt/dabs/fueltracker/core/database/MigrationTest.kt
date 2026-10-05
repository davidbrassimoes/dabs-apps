package pt.dabs.fueltracker.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsRowsAndDefaultsDiscountToZero() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.insert(
                "fill_ups",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("date_time", 1_000L)
                    put("odometer_km", 162_259)
                    put("volume_ml", 42_630)
                    put("price_per_liter_milli_cents", 229_900)
                    put("total_cents", 9_801L)
                    put("is_full_tank", 1)
                    put("atcud", "ABCD1234-1")
                    put("created_at", 1_000L)
                },
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT odometer_km, total_cents, discount_cents, atcud FROM fill_ups").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(162_259, cursor.getInt(0))
                assertEquals(9_801L, cursor.getLong(1))
                assertEquals(0L, cursor.getLong(2))
                assertEquals("ABCD1234-1", cursor.getString(3))
            }
        }
    }

    @Test
    fun migrate2To3KeepsRowsWithoutTrip() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.insert(
                "fill_ups",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("date_time", 1_000L)
                    put("odometer_km", 162_259)
                    put("volume_ml", 42_630)
                    put("price_per_liter_milli_cents", 229_900)
                    put("total_cents", 9_588L)
                    put("discount_cents", 213L)
                    put("is_full_tank", 1)
                    put("created_at", 1_000L)
                },
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT discount_cents, trip_tenths_km FROM fill_ups").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(213L, cursor.getLong(0))
                assertTrue(cursor.isNull(1))
            }
        }
    }

    @Test
    fun migrate3To4AddsBrandAndFuelAsNull() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insert(
                "fill_ups",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("date_time", 1_000L)
                    put("odometer_km", 162_259)
                    put("volume_ml", 42_630)
                    put("price_per_liter_milli_cents", 229_900)
                    put("total_cents", 9_588L)
                    put("discount_cents", 213L)
                    put("trip_tenths_km", 6_020)
                    put("is_full_tank", 1)
                    put("station_name", "Repsol Loures")
                    put("created_at", 1_000L)
                },
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT trip_tenths_km, station_name, brand, fuel_type, fuel_premium FROM fill_ups").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(6_020, cursor.getInt(0))
                assertEquals("Repsol Loures", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
        }
    }

    /** Todas as migrações juntas abrem a base de dados atual pelo Room (como a app faz). */
    @Test
    fun migrateAllOpensWithRoom() = runBlocking {
        helper.createDatabase(TEST_DB, 1).close()
        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val id = database.fillUpDao().insert(
                FillUpEntity(
                    dateTimeEpochMillis = 2_000L,
                    odometerKm = 100,
                    volumeMl = 42_630,
                    pricePerLiterMilliCents = 229_900,
                    totalCents = 9_588,
                    discountCents = 213,
                    tripTenthsKm = 6_020,
                    brand = "REPSOL",
                    fuelType = "GASOLINA_95",
                    fuelPremium = true,
                    createdAtEpochMillis = 2_000L,
                )
            )
            assertEquals(213L, database.fillUpDao().getById(id)!!.discountCents)
            assertEquals(6_020, database.fillUpDao().getById(id)!!.tripTenthsKm)
            assertEquals("REPSOL", database.fillUpDao().getById(id)!!.brand)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
