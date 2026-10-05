package pt.dabs.fueltracker.core.scanning

import pt.dabs.fueltracker.core.domain.model.FuelBrand
import java.text.Normalizer
import java.util.Locale

/**
 * Identifica o posto a partir do texto OCR de um talão: a marca, da lista fixa [FuelBrand], e o
 * local, se estiver na mesma linha ("E1146 - A.S. REPSOL LOURES B-SENTIDO N/S" → Repsol,
 * "Loures B-Sentido N/S").
 *
 * A primeira linha do talão é muitas vezes a empresa que explora o posto (ex.: "GESPOST … LDA"),
 * não a marca; linhas de empresa, morada e contactos só servem se não houver outra com a marca.
 * É uma sugestão: o utilizador confirma ou corrige.
 */
object ReceiptStationParser {

    /** Linhas que são empresa, morada ou contactos, não a identificação do posto. */
    private val COMPANY_OR_ADDRESS = Regex("""\bLDA\b|\bS\.?\s?A\.?$|\bS\.?A\.?\b|UNIPESSOAL|\bNIF\b|CONTRIBUINTE|CAP\.? ?SOC|C\.?R\.?C|\bTEL\b|\bTLF\b|\bFAX\b|\bAV\.?\b|\bAVENIDA\b|\bRUA\b|\bESTRADA\b|\bKM\b|\d{4}-\d{3}|WWW\.|@""")

    /** Palavras que não fazem parte do nome do local. */
    private val FILLER = setOf(
        "A.S.", "AS", "E.S.", "ES", "POSTO", "POSTOS", "AREA", "ESTACAO", "DE", "SERVICO", "SERVICOS",
        "ABASTECIMENTO", "COMBUSTIVEIS", "BOMBA", "BOMBAS", "-", "–", "/",
    )

    /** Marca e local lidos do talão; [place] pode ser vazio (só a marca). */
    data class StationSuggestion(val brand: FuelBrand, val place: String)

    fun suggest(rows: List<String>): StationSuggestion? {
        var bestBrand: FuelBrand? = null
        var bestPlace = ""
        var bestRank = Int.MAX_VALUE
        val brands = FuelBrand.entries.filter { it.aliases != null }
        for (row in rows) {
            // Palavras originais (com acentos, para o nome) e normalizadas (para comparar).
            val words = row.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val normalizedWords = words.map { normalize(it) }
            val line = normalizedWords.joinToString(" ")
            for ((index, brand) in brands.withIndex()) {
                val match = brand.aliases!!.find(line) ?: continue
                // Linhas próprias do posto primeiro; empresa/morada só como último recurso (só a marca).
                val ownLine = !COMPANY_OR_ADDRESS.containsMatchIn(line)
                val rank = (if (ownLine) 0 else 1_000) + index
                if (rank < bestRank) {
                    bestRank = rank
                    bestBrand = brand
                    bestPlace = if (ownLine) location(words, normalizedWords, match.range) else ""
                }
            }
        }
        return bestBrand?.let { StationSuggestion(it, bestPlace) }
    }

    /**
     * As palavras da linha fora da marca ([brandRange], posições em [normalizedWords] juntas por
     * espaços), sem códigos de posto nem palavras genéricas, em maiúsculas iniciais.
     */
    private fun location(words: List<String>, normalizedWords: List<String>, brandRange: IntRange): String {
        var position = 0
        val kept = ArrayList<String>()
        for ((i, normalizedWord) in normalizedWords.withIndex()) {
            val start = position
            val end = position + normalizedWord.length - 1
            position += normalizedWord.length + 1
            if (start <= brandRange.last && end >= brandRange.first) continue
            val word = normalizedWord.trim(',', ';', ':')
            val isCode = (word.any { it.isDigit() } && word.count { it.isLetter() } <= 1) || word.matches(Regex("""[A-Z]\d+"""))
            if (word.isEmpty() || word in FILLER || isCode) continue
            kept += words[i].trim(',', ';', ':')
        }
        return kept.take(MAX_PLACE_WORDS).joinToString(" ") { titleCase(it) }
    }

    private fun titleCase(word: String): String {
        // Siglas com barra ("N/S") e palavras de uma letra ficam como estão.
        if (word.contains('/') || word.length <= 1) return word
        return word.split('-').joinToString("-") { part ->
            part.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    private fun normalize(row: String): String {
        val noAccents = Normalizer.normalize(row, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
        return noAccents.uppercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()
    }

    private const val MAX_PLACE_WORDS = 4
}
