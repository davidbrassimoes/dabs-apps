package pt.dabs.fueltracker.core.domain

/**
 * Conversão de texto introduzido pelo utilizador (formato pt-PT, vírgula decimal,
 * mas tolerante ao ponto) para inteiros com escala fixa. Sem floats.
 */
object NumberInput {

    /**
     * Interpreta [text] como decimal e devolve o valor × 10^[scale], arredondado half-up
     * se tiver mais casas do que [scale]. Devolve null para texto vazio ou inválido.
     * Espaços são ignorados; aceita "," ou "." como separador decimal, mas não ambos.
     */
    fun parseScaled(text: String, scale: Int): Long? {
        require(scale >= 0)
        val cleaned = text.filterNot { it == ' ' || it == ' ' || it == ' ' }
        if (cleaned.isEmpty()) return null
        val hasComma = cleaned.contains(',')
        val hasDot = cleaned.contains('.')
        if (hasComma && hasDot) return null
        val normalized = cleaned.replace(',', '.')
        if (normalized.count { it == '.' } > 1) return null
        val parts = normalized.split('.')
        val integerPart = parts[0]
        val fractionPart = parts.getOrElse(1) { "" }
        if (integerPart.isEmpty() && fractionPart.isEmpty()) return null
        if (!integerPart.all { it.isAsciiDigit() } || !fractionPart.all { it.isAsciiDigit() }) return null
        if (integerPart.length > 12) return null

        val integerValue = if (integerPart.isEmpty()) 0L else integerPart.toLong()
        var result = integerValue
        repeat(scale) { result *= 10 }

        val fractionDigits = fractionPart.take(scale).padEnd(scale, '0')
        if (scale > 0) result += fractionDigits.toLong()

        // Arredondamento half-up pelo dígito seguinte ao último considerado.
        val roundingDigit = fractionPart.getOrNull(scale)
        if (roundingDigit != null && roundingDigit >= '5') result += 1
        return result
    }

    /** "40,5" → 40 500 ml. */
    fun parseLitersToMl(text: String): Int? = parseScaled(text, 3)?.toIntOrNull()

    /** "1,749" €/L → 174 900 milésimos de cêntimo. */
    fun parsePricePerLiterToMilliCents(text: String): Int? = parseScaled(text, 5)?.toIntOrNull()

    /** "69,96" → 6 996 cêntimos. */
    fun parseEurosToCents(text: String): Long? = parseScaled(text, 2)

    /** "123456" ou "123 456" → 123456 km. Só dígitos. */
    fun parseOdometerKm(text: String): Int? {
        val cleaned = text.filterNot { it == ' ' || it == ' ' || it == ' ' }
        if (cleaned.isEmpty() || !cleaned.all { it.isAsciiDigit() } || cleaned.length > 7) return null
        return cleaned.toInt()
    }

    /** Valor inteiro com escala para texto editável: 40500 (escala 3) → "40,500". */
    fun formatScaled(value: Long, scale: Int, trimTrailingZeros: Boolean = false): String {
        require(scale >= 0)
        val negative = value < 0
        val digits = kotlin.math.abs(value).toString().padStart(scale + 1, '0')
        val integerPart = digits.dropLast(scale)
        var fractionPart = digits.takeLast(scale)
        if (trimTrailingZeros) fractionPart = fractionPart.trimEnd('0')
        val body = if (fractionPart.isEmpty()) integerPart else "$integerPart,$fractionPart"
        return if (negative) "-$body" else body
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun Long.toIntOrNull(): Int? = if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else null
}
