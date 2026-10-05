package pt.dabs.fueltracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FillUpEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fillUpDao(): FillUpDao

    companion object {
        const val NAME = "fueltracker.db"
    }
}
