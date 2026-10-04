package pt.dabs.fueltracker.core.domain

import pt.dabs.fueltracker.core.domain.model.FillUp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val LISBON: ZoneId = ZoneId.of("Europe/Lisbon")

fun at(date: String, time: String = "12:00"): Instant =
    LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.parse(time)).atZone(LISBON).toInstant()

/**
 * Abastecimento de teste. Se [totalCents] for omitido, calcula-se a partir de volume × preço.
 */
fun fillUp(
    id: Long,
    date: String,
    odometerKm: Int,
    volumeMl: Int,
    pricePerLiterMilliCents: Int = 170_000,
    totalCents: Long = FuelMath.totalCents(volumeMl, pricePerLiterMilliCents),
    isFullTank: Boolean = true,
    atcud: String? = null,
    time: String = "12:00",
): FillUp = FillUp(
    id = id,
    dateTime = at(date, time),
    odometerKm = odometerKm,
    volumeMl = volumeMl,
    pricePerLiterMilliCents = pricePerLiterMilliCents,
    totalCents = totalCents,
    isFullTank = isFullTank,
    atcud = atcud,
    createdAt = at(date, time),
)
