package pt.dabs.fueltracker.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.dabs.fueltracker.core.database.FillUpDao
import pt.dabs.fueltracker.core.database.FillUpEntity
import pt.dabs.fueltracker.core.domain.model.FillUp
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomFillUpRepository @Inject constructor(
    private val dao: FillUpDao,
    private val widgetRefresher: WidgetRefresher,
) : FillUpRepository {

    override fun observeAll(): Flow<List<FillUp>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): FillUp? = dao.getById(id)?.toDomain()

    override suspend fun insert(fillUp: FillUp): Long =
        dao.insert(fillUp.toEntity()).also { widgetRefresher.refresh() }

    override suspend fun update(fillUp: FillUp) {
        dao.update(fillUp.toEntity())
        widgetRefresher.refresh()
    }

    override suspend fun delete(fillUp: FillUp) {
        dao.delete(fillUp.toEntity())
        widgetRefresher.refresh()
    }

    override suspend fun isAtcudUsed(atcud: String, excludeId: Long): Boolean =
        dao.existsAtcud(atcud, excludeId)
}

internal fun FillUpEntity.toDomain() = FillUp(
    id = id,
    dateTime = Instant.ofEpochMilli(dateTimeEpochMillis),
    odometerKm = odometerKm,
    volumeMl = volumeMl,
    pricePerLiterMilliCents = pricePerLiterMilliCents,
    totalCents = totalCents,
    isFullTank = isFullTank,
    stationNif = stationNif,
    stationName = stationName,
    atcud = atcud,
    buyerNifPresent = buyerNifPresent,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

internal fun FillUp.toEntity() = FillUpEntity(
    id = id,
    dateTimeEpochMillis = dateTime.toEpochMilli(),
    odometerKm = odometerKm,
    volumeMl = volumeMl,
    pricePerLiterMilliCents = pricePerLiterMilliCents,
    totalCents = totalCents,
    isFullTank = isFullTank,
    stationNif = stationNif,
    stationName = stationName,
    atcud = atcud,
    buyerNifPresent = buyerNifPresent,
    notes = notes,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)
