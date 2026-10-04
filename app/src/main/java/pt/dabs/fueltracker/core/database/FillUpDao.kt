package pt.dabs.fueltracker.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FillUpDao {

    /** Ordem cronológica ascendente (a ordem que o domínio espera). */
    @Query("SELECT * FROM fill_ups ORDER BY date_time ASC, odometer_km ASC, id ASC")
    fun observeAll(): Flow<List<FillUpEntity>>

    @Query("SELECT * FROM fill_ups WHERE id = :id")
    suspend fun getById(id: Long): FillUpEntity?

    @Query("SELECT COUNT(*) FROM fill_ups")
    fun observeCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM fill_ups WHERE atcud = :atcud AND id != :excludeId)")
    suspend fun existsAtcud(atcud: String, excludeId: Long): Boolean

    /** Insere; com id ≠ 0 mantém o id (usado para repor um registo apagado). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FillUpEntity): Long

    @Update
    suspend fun update(entity: FillUpEntity)

    @Delete
    suspend fun delete(entity: FillUpEntity)
}
