package pt.dabs.fueltracker.core.scanning

import pt.dabs.fueltracker.core.domain.Nif
import pt.dabs.fueltracker.core.domain.NumberInput
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Conteúdo relevante do código QR das faturas portuguesas
 * (Portaria n.º 195/2020, "Especificações Técnicas – Código QR", v1.1).
 *
 * Formato: campos `CÓDIGO:valor` concatenados por `*`, pela ordem da tabela da AT.
 * Montantes com `.` decimal e duas casas; datas `AAAAMMDD`.
 */
data class AtQrCode(
    /** A: NIF do emitente (o posto). */
    val issuerNif: String,
    /** B: NIF do adquirente; 999999990 = consumidor final sem NIF. */
    val buyerNif: String,
    /** C: país do adquirente. */
    val buyerCountry: String?,
    /** D: tipo de documento SAF-T (FT, FS, FR, ...). */
    val documentType: String?,
    /** E: estado do documento (N normal, A anulado, ...). */
    val documentStatus: String?,
    /** F: data do documento. */
    val documentDate: LocalDate,
    /** G: identificação única do documento (ex.: "FS CDVF/12345"). */
    val documentId: String?,
    /** H: ATCUD. */
    val atcud: String,
    /** N: total de impostos, em cêntimos. */
    val totalTaxesCents: Long?,
    /** O: total do documento com impostos, em cêntimos. */
    val totalCents: Long,
    /** Q: 4 carateres do hash. */
    val hash: String?,
    /** R: n.º do certificado do software. */
    val certificate: String?,
    /** S: outras informações (livre, elementos separados por `;`). */
    val otherInfo: String?,
    /** Todos os campos tal como lidos, para diagnóstico. */
    val rawFields: Map<String, String>,
) {
    val buyerIsFinalConsumer: Boolean get() = buyerNif == Nif.FINAL_CONSUMER

    /** true se a fatura foi emitida com o NIF [userNif]. */
    fun hasBuyerNif(userNif: String): Boolean = !buyerIsFinalConsumer && buyerNif == userNif
}

sealed interface AtQrParseResult {
    data class Success(val code: AtQrCode) : AtQrParseResult
    data class Failure(val reason: Reason, val detail: String? = null) : AtQrParseResult

    enum class Reason {
        /** O texto não tem a estrutura `CÓDIGO:valor*...` de um QR da AT. */
        NOT_AT_QR_CODE,
        MISSING_REQUIRED_FIELD,
        INVALID_DATE,
        INVALID_AMOUNT,
        INVALID_NIF,
    }
}

object AtQrCodeParser {

    private const val FIELD_SEPARATOR = '*'
    private const val KEY_VALUE_SEPARATOR = ':'
    private val KEY_PATTERN = Regex("^[A-S][1-8]?$")
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Campos sem os quais o talão não serve para registar um abastecimento. */
    private val REQUIRED = listOf("A", "B", "F", "H", "O")

    fun parse(raw: String): AtQrParseResult {
        val fields = splitFields(raw)
        if (fields.isEmpty() || !fields.containsKey("A")) {
            return AtQrParseResult.Failure(AtQrParseResult.Reason.NOT_AT_QR_CODE)
        }
        for (key in REQUIRED) {
            if (fields[key].isNullOrBlank()) {
                return AtQrParseResult.Failure(AtQrParseResult.Reason.MISSING_REQUIRED_FIELD, key)
            }
        }

        val issuerNif = Nif.normalize(fields.getValue("A"))
            ?: return AtQrParseResult.Failure(AtQrParseResult.Reason.INVALID_NIF, "A")
        if (issuerNif.length != Nif.LENGTH) {
            return AtQrParseResult.Failure(AtQrParseResult.Reason.INVALID_NIF, "A")
        }
        val buyerNif = fields.getValue("B").trim()

        val date = try {
            LocalDate.parse(fields.getValue("F").trim(), DATE_FORMAT)
        } catch (e: DateTimeParseException) {
            return AtQrParseResult.Failure(AtQrParseResult.Reason.INVALID_DATE, fields["F"])
        }

        val totalCents = parseAmount(fields.getValue("O"))
            ?: return AtQrParseResult.Failure(AtQrParseResult.Reason.INVALID_AMOUNT, "O")
        val totalTaxesCents = fields["N"]?.let { parseAmount(it) }

        return AtQrParseResult.Success(
            AtQrCode(
                issuerNif = issuerNif,
                buyerNif = buyerNif,
                buyerCountry = fields["C"],
                documentType = fields["D"],
                documentStatus = fields["E"],
                documentDate = date,
                documentId = fields["G"],
                atcud = fields.getValue("H").trim(),
                totalTaxesCents = totalTaxesCents,
                totalCents = totalCents,
                hash = fields["Q"],
                certificate = fields["R"],
                otherInfo = fields["S"],
                rawFields = fields,
            )
        )
    }

    /**
     * Divide em pares código→valor. Partes sem `:` ou com código fora de A..S são ignoradas,
     * para tolerar lixo à volta. Em códigos repetidos vale o primeiro.
     */
    private fun splitFields(raw: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (part in raw.trim().split(FIELD_SEPARATOR)) {
            val index = part.indexOf(KEY_VALUE_SEPARATOR)
            if (index <= 0) continue
            val key = part.substring(0, index).trim()
            if (!KEY_PATTERN.matches(key)) continue
            val value = part.substring(index + 1).trim()
            result.putIfAbsent(key, value)
        }
        return result
    }

    /** "513600.58" → 51 360 058. Tolera vírgula decimal e espaços, mas não separador de milhares. */
    private fun parseAmount(text: String): Long? = NumberInput.parseScaled(text, 2)
}
