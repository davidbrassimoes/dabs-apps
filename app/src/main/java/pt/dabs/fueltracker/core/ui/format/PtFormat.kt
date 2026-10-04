package pt.dabs.fueltracker.core.ui.format

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatação para apresentação em pt-PT. Só a UI usa isto; o domínio trabalha em inteiros. */
object PtFormat {

    val locale: Locale = Locale.forLanguageTag("pt-PT")

    private val symbols = DecimalFormatSymbols(locale)

    private fun decimal(pattern: String) = DecimalFormat(pattern, symbols)

    private val twoDecimals = decimal("#,##0.00")
    private val threeDecimals = decimal("#,##0.000")
    private val oneDecimal = decimal("#,##0.0")
    private val integer = decimal("#,##0")

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale)
    private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    /** 6996 → "69,96 €" */
    fun euros(cents: Long): String = twoDecimals.format(BigDecimal.valueOf(cents, 2)) + " €"

    /** 40500 → "40,50 L" */
    fun liters(ml: Long): String = twoDecimals.format(BigDecimal.valueOf(ml, 3)) + " L"

    /** 174900 → "1,749 €/L" */
    fun pricePerLiter(milliCents: Int): String =
        threeDecimals.format(BigDecimal.valueOf(milliCents.toLong(), 5)) + " €/L"

    /** 6670 → "6,7 L/100 km" (uma casa decimal, o que o painel de um carro mostra) */
    fun consumption(mlPer100Km: Int): String =
        oneDecimal.format(BigDecimal.valueOf(mlPer100Km.toLong(), 3)) + " L/100 km"

    /** 6670 → "6,67" (duas casas; para tabelas) */
    fun consumptionPrecise(mlPer100Km: Int): String =
        twoDecimals.format(BigDecimal.valueOf(mlPer100Km.toLong(), 3))

    /** 123456 → "123 456 km" */
    fun km(km: Int): String = integer.format(km.toLong()) + " km"

    /** 11700 → "0,117 €/km" */
    fun costPerKm(milliCentsPerKm: Long): String =
        threeDecimals.format(BigDecimal.valueOf(milliCentsPerKm, 5)) + " €/km"

    fun date(instant: Instant, zone: ZoneId): String = dateFormatter.format(instant.atZone(zone))

    fun dateTime(instant: Instant, zone: ZoneId): String = dateTimeFormatter.format(instant.atZone(zone))

    fun month(yearMonth: java.time.YearMonth): String =
        monthFormatter.format(yearMonth).replaceFirstChar { it.titlecase(locale) }
}
