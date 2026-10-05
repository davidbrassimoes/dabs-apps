package pt.dabs.fueltracker.core.csv

import pt.dabs.fueltracker.core.domain.NumberInput
import pt.dabs.fueltracker.core.domain.model.FillUp
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * CSV de abastecimentos para cópia de segurança e folha de cálculo.
 *
 * Separador `;`, decimal com vírgula (pt-PT), datas em ISO-8601 UTC, booleanos 1/0.
 * A leitura é tolerante: aceita `,` ou `.` como decimal e várias grafias de booleano.
 */
object FillUpCsv {

    const val DELIMITER = ';'
    const val MIME_TYPE = "text/csv"

    /** Marca de ordem de bytes UTF-8: o Excel abre o ficheiro com a codificação certa. */
    val BOM: String = 0xFEFF.toChar().toString()

    val HEADER: List<String> = listOf(
        "id", "data_hora", "odometro_km", "parcial_km", "litros", "preco_litro", "total_eur", "desconto_eur", "deposito_cheio",
        "nif_posto", "nome_posto", "atcud", "fatura_com_nif", "notas", "criado_em",
    )

    data class RowError(val line: Int, val reason: String)

    data class ParseResult(val fillUps: List<FillUp>, val errors: List<RowError>)

    /** O que importar: linhas novas e quantas se ignoram por já existirem. */
    data class ImportPlan(val toInsert: List<FillUp>, val skippedDuplicates: Int)

    fun export(fillUps: List<FillUp>): String {
        val builder = StringBuilder()
        builder.append(BOM)
        builder.append(HEADER.joinToString(DELIMITER.toString())).append("\r\n")
        for (fillUp in fillUps) {
            val fields = listOf(
                fillUp.id.toString(),
                fillUp.dateTime.toString(),
                fillUp.odometerKm.toString(),
                fillUp.tripTenthsKm?.let { NumberInput.formatScaled(it.toLong(), 1) }.orEmpty(),
                NumberInput.formatScaled(fillUp.volumeMl.toLong(), 3),
                NumberInput.formatScaled(fillUp.pricePerLiterMilliCents.toLong(), 5),
                NumberInput.formatScaled(fillUp.totalCents, 2),
                NumberInput.formatScaled(fillUp.discountCents, 2),
                if (fillUp.isFullTank) "1" else "0",
                fillUp.stationNif.orEmpty(),
                fillUp.stationName.orEmpty(),
                fillUp.atcud.orEmpty(),
                fillUp.buyerNifPresent?.let { if (it) "1" else "0" }.orEmpty(),
                fillUp.notes.orEmpty(),
                fillUp.createdAt.toString(),
            )
            builder.append(fields.joinToString(DELIMITER.toString()) { quote(it) }).append("\r\n")
        }
        return builder.toString()
    }

    fun parse(text: String): ParseResult {
        val records = splitRecords(text.removePrefix(BOM))
        if (records.isEmpty()) return ParseResult(emptyList(), listOf(RowError(1, "ficheiro vazio")))
        val header = records.first().fields.map { it.trim().lowercase() }
        val index = HEADER.associateWith { header.indexOf(it) }
        val missing = listOf("data_hora", "odometro_km", "litros", "preco_litro", "total_eur").filter { index.getValue(it) < 0 }
        if (missing.isNotEmpty()) {
            return ParseResult(emptyList(), listOf(RowError(1, "cabeçalho sem as colunas: ${missing.joinToString()}")))
        }

        val fillUps = ArrayList<FillUp>()
        val errors = ArrayList<RowError>()
        for (record in records.drop(1)) {
            if (record.fields.all { it.isBlank() }) continue
            fun field(name: String): String? = index.getValue(name).takeIf { it >= 0 }?.let { record.fields.getOrNull(it)?.trim() }
            try {
                val dateTime = parseInstant(field("data_hora")) ?: throw CsvFieldException("data_hora")
                val odometer = NumberInput.parseOdometerKm(field("odometro_km").orEmpty()) ?: throw CsvFieldException("odometro_km")
                // Coluna opcional (ficheiros anteriores à v3 não a têm): vazio = parcial não registado.
                val trip = field("parcial_km").let { text ->
                    if (text.isNullOrBlank()) null
                    else NumberInput.parseScaled(text, 1)?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: throw CsvFieldException("parcial_km")
                }
                val volume = NumberInput.parseLitersToMl(field("litros").orEmpty())?.takeIf { it > 0 } ?: throw CsvFieldException("litros")
                val price = NumberInput.parsePricePerLiterToMilliCents(field("preco_litro").orEmpty())?.takeIf { it > 0 } ?: throw CsvFieldException("preco_litro")
                val total = NumberInput.parseEurosToCents(field("total_eur").orEmpty())?.takeIf { it > 0 } ?: throw CsvFieldException("total_eur")
                // Coluna opcional (ficheiros anteriores à v2 não a têm): vazio = sem desconto.
                val discount = field("desconto_eur").let { text ->
                    if (text.isNullOrBlank()) 0L
                    else NumberInput.parseEurosToCents(text)?.takeIf { it >= 0 } ?: throw CsvFieldException("desconto_eur")
                }
                val fullTank = parseBoolean(field("deposito_cheio")) ?: true
                fillUps += FillUp(
                    id = FillUp.NEW_ID,
                    dateTime = dateTime,
                    odometerKm = odometer,
                    volumeMl = volume,
                    pricePerLiterMilliCents = price,
                    totalCents = total,
                    discountCents = discount,
                    tripTenthsKm = trip,
                    isFullTank = fullTank,
                    stationNif = field("nif_posto")?.ifBlank { null },
                    stationName = field("nome_posto")?.ifBlank { null },
                    atcud = field("atcud")?.ifBlank { null },
                    buyerNifPresent = parseBoolean(field("fatura_com_nif")),
                    notes = field("notas")?.ifBlank { null },
                    createdAt = parseInstant(field("criado_em")) ?: dateTime,
                )
            } catch (e: CsvFieldException) {
                errors += RowError(record.line, "valor inválido em ${e.field}")
            }
        }
        return ParseResult(fillUps, errors)
    }

    /**
     * Ignora linhas cujo ATCUD já existe ou que coincidem em data e odómetro com um registo
     * existente (ou com outra linha do próprio ficheiro).
     */
    fun planImport(existing: List<FillUp>, parsed: List<FillUp>): ImportPlan {
        val knownAtcuds = existing.mapNotNullTo(HashSet()) { it.atcud }
        val knownKeys = existing.mapTo(HashSet()) { it.dateTime to it.odometerKm }
        val toInsert = ArrayList<FillUp>()
        var skipped = 0
        for (fillUp in parsed) {
            val key = fillUp.dateTime to fillUp.odometerKm
            val duplicate = (fillUp.atcud != null && fillUp.atcud in knownAtcuds) || key in knownKeys
            if (duplicate) {
                skipped++
                continue
            }
            fillUp.atcud?.let { knownAtcuds += it }
            knownKeys += key
            toInsert += fillUp
        }
        return ImportPlan(toInsert, skipped)
    }

    // --- detalhes ---------------------------------------------------------------

    private class CsvFieldException(val field: String) : Exception(field)

    private data class Record(val line: Int, val fields: List<String>)

    private fun quote(value: String): String =
        if (value.any { it == DELIMITER || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Divide em registos respeitando aspas (campos com `;`, aspas duplicadas e quebras de linha). */
    private fun splitRecords(text: String): List<Record> {
        val records = ArrayList<Record>()
        val fields = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var i = 0
        fun endField() { fields += current.toString(); current.setLength(0) }
        fun endRecord() {
            endField()
            if (fields.size > 1 || fields.single().isNotBlank()) records += Record(recordLine, ArrayList(fields))
            fields.clear()
            recordLine = line
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
                inQuotes && c == '"' -> inQuotes = false
                !inQuotes && c == '"' && current.isEmpty() -> inQuotes = true
                !inQuotes && c == DELIMITER -> endField()
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    line++
                    endRecord()
                }
                else -> {
                    current.append(c)
                    if (c == '\n') line++
                }
            }
            i++
        }
        if (current.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }

    private fun parseInstant(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            try {
                java.time.OffsetDateTime.parse(text).toInstant()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    private fun parseBoolean(text: String?): Boolean? = when (text?.trim()?.lowercase()) {
        null, "" -> null
        "1", "true", "sim", "s", "yes", "y" -> true
        "0", "false", "não", "nao", "n", "no" -> false
        else -> null
    }
}
