package pt.dabs.fueltracker.core.scanning

/**
 * Extrai leituras do conta-km total a partir do texto OCR de um visor LCD de sete segmentos.
 * Funções puras; a estabilização entre frames está em [OdometerStabilizer].
 */
object OdometerReader {

    const val MIN_DIGITS = 4
    const val MAX_DIGITS = 7
    const val DEFAULT_MAX_DISTANCE_KM = 3_000

    /** Confusões típicas do OCR em dígitos de sete segmentos. Só se aplicam a tokens dominados por dígitos. */
    private val CONFUSIONS = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', '|' to '1', 'i' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'B' to '8',
        'G' to '6',
    )

    /**
     * Candidatos a odómetro total em [rows], por ordem de aparição. Aceita grupos de milhares com
     * espaço ou ponto ("123 456", "123.456") e o sufixo "km". Rejeita números com parte decimal
     * ("1234,5" é o parcial) e tokens com mais letras do que dígitos.
     */
    fun extractCandidates(rows: List<String>): List<Int> {
        val result = ArrayList<Int>()
        for (row in rows) {
            val cleaned = row.replace(' ', ' ').replace(Regex("""(?i)\s*km\b"""), "")
            // Junta grupos de milhares "123 456" / "123.456" antes de dividir por espaços.
            val joined = cleaned.replace(Regex("""(?<=\d)[ .](?=\d{3}(?![\d.,]))"""), "")
            for (token in joined.split(Regex("""\s+"""))) {
                if (token.isEmpty()) continue
                if (token.contains(',') || token.contains('.')) continue // parcial ou decimal
                val digits = token.count { it.isDigit() }
                if (digits == 0 || digits * 2 < token.length) continue
                val repaired = token.map { CONFUSIONS[it] ?: it }.joinToString("")
                if (!repaired.all { it.isDigit() }) continue
                if (repaired.length !in MIN_DIGITS..MAX_DIGITS) continue
                repaired.toIntOrNull()?.let { result += it }
            }
        }
        return result
    }

    /**
     * Escolhe o candidato plausível: maior do que [lastOdometerKm] e no máximo [maxDistanceKm] à frente.
     * Sem odómetro anterior aceita qualquer valor com pelo menos [MIN_DIGITS] dígitos.
     * Entre vários, prefere o que tem mais dígitos (o total tem sempre mais do que o parcial).
     */
    fun pickPlausible(
        candidates: List<Int>,
        lastOdometerKm: Int?,
        maxDistanceKm: Int = DEFAULT_MAX_DISTANCE_KM,
    ): Int? {
        val plausible = candidates.filter { value ->
            if (lastOdometerKm != null) value > lastOdometerKm && value <= lastOdometerKm + maxDistanceKm
            else value >= 1_000
        }
        return plausible.maxWithOrNull(compareBy({ it.toString().length }, { -it }))
    }

    /** Conveniência: extrai e escolhe num só passo. */
    fun read(rows: List<String>, lastOdometerKm: Int?, maxDistanceKm: Int = DEFAULT_MAX_DISTANCE_KM): Int? =
        pickPlausible(extractCandidates(rows), lastOdometerKm, maxDistanceKm)
}

/**
 * Fixa uma leitura quando o mesmo valor aparece em [requiredFrames] frames consecutivos.
 * Frames sem leitura não contam nem quebram a sequência, até [maxGap] seguidos.
 */
class OdometerStabilizer(
    private val requiredFrames: Int = 3,
    private val maxGap: Int = 2,
) {
    private var current: Int? = null
    private var count = 0
    private var gap = 0

    /** Valor atualmente em contagem (para mostrar ao utilizador), ou null. */
    val candidate: Int? get() = current

    /** Progresso 0..1 da estabilização do candidato atual. */
    val progress: Float get() = if (current == null) 0f else count.toFloat() / requiredFrames

    /** Regista a leitura de um frame; devolve o valor fixado quando estabiliza, senão null. */
    fun offer(value: Int?): Int? {
        if (value == null) {
            gap++
            if (gap > maxGap) reset()
            return null
        }
        gap = 0
        if (value == current) {
            count++
        } else {
            current = value
            count = 1
        }
        return if (count >= requiredFrames) value else null
    }

    fun reset() {
        current = null
        count = 0
        gap = 0
    }
}
