package pt.dabs.fueltracker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2: descontos do talão (cêntimos, positivos); litros × preço = total + desconto. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE fill_ups ADD COLUMN discount_cents INTEGER NOT NULL DEFAULT 0")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
