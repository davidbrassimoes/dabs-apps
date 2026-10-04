package pt.dabs.fueltracker.core.domain

/** Validação do NIF português (9 dígitos, dígito de controlo módulo 11). */
object Nif {

    const val LENGTH = 9

    /** NIF usado pela AT para "consumidor final" sem identificação. */
    const val FINAL_CONSUMER = "999999990"

    /** Remove espaços e devolve só dígitos, ou null se houver outros caracteres. */
    fun normalize(text: String): String? {
        val cleaned = text.filterNot { it == ' ' || it == ' ' }
        if (cleaned.isEmpty() || !cleaned.all { it in '0'..'9' }) return null
        return cleaned
    }

    fun isValid(text: String): Boolean {
        val digits = normalize(text) ?: return false
        if (digits.length != LENGTH) return false
        if (digits[0] == '0') return false
        val sum = (0 until 8).sumOf { index -> (digits[index] - '0') * (9 - index) }
        val remainder = sum % 11
        val check = if (remainder < 2) 0 else 11 - remainder
        return (digits[8] - '0') == check
    }
}
