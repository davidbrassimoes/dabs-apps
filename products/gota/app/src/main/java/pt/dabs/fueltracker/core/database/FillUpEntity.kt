package pt.dabs.fueltracker.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fill_ups",
    indices = [
        Index(value = ["date_time"]),
        Index(value = ["odometer_km"]),
        // NULLs são distintos em índices únicos do SQLite: só ATCUDs presentes ficam únicos.
        Index(value = ["atcud"], unique = true),
    ],
)
data class FillUpEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Instante do talão em epoch millis (UTC). */
    @ColumnInfo(name = "date_time")
    val dateTimeEpochMillis: Long,
    @ColumnInfo(name = "odometer_km")
    val odometerKm: Int,
    @ColumnInfo(name = "volume_ml")
    val volumeMl: Int,
    @ColumnInfo(name = "price_per_liter_milli_cents")
    val pricePerLiterMilliCents: Int,
    @ColumnInfo(name = "total_cents")
    val totalCents: Long,
    /** Descontos do talão (positivos); litros × preço = total + desconto. */
    @ColumnInfo(name = "discount_cents", defaultValue = "0")
    val discountCents: Long = 0,
    /** Conta-km parcial ao abastecer, em décimas de km (posto a zeros a cada abastecimento). */
    @ColumnInfo(name = "trip_tenths_km")
    val tripTenthsKm: Int? = null,
    @ColumnInfo(name = "is_full_tank", defaultValue = "1")
    val isFullTank: Boolean = true,
    @ColumnInfo(name = "station_nif")
    val stationNif: String? = null,
    @ColumnInfo(name = "station_name")
    val stationName: String? = null,
    /** Código de [pt.dabs.fueltracker.core.domain.model.FuelBrand]. */
    @ColumnInfo(name = "brand")
    val brand: String? = null,
    /** Código de [pt.dabs.fueltracker.core.domain.model.FuelType]. */
    @ColumnInfo(name = "fuel_type")
    val fuelType: String? = null,
    @ColumnInfo(name = "fuel_premium")
    val fuelPremium: Boolean? = null,
    @ColumnInfo(name = "atcud")
    val atcud: String? = null,
    @ColumnInfo(name = "buyer_nif_present")
    val buyerNifPresent: Boolean? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long,
)
