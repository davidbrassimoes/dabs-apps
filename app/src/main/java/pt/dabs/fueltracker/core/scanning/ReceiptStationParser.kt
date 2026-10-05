package pt.dabs.fueltracker.core.scanning

import java.text.Normalizer
import java.util.Locale

/**
 * Sugere o nome do posto a partir do texto OCR de um talão: a marca (Galp, Repsol, BP…) e, se
 * estiver na mesma linha, o local ("E1146 - A.S. REPSOL LOURES B-SENTIDO N/S" →
 * "Repsol Loures B-Sentido N/S").
 *
 * A primeira linha do talão é muitas vezes a empresa que explora o posto (ex.: "GESPOST … LDA"),
 * não a marca; linhas de empresa, morada e contactos só servem se não houver outra com a marca.
 * É uma sugestão: o utilizador confirma ou corrige.
 */
object ReceiptStationParser {

    /** Marca → expressão sobre o texto em maiúsculas sem acentos. A ordem resolve empates. */
    private val BRANDS: List<Pair<String, Regex>> = listOf(
        "Galp" to Regex("""\bGALP\b|\bPETROGAL\b"""),
        "Repsol" to Regex("""\bREPSOL\b"""),
        "BP" to Regex("""\bBP\b"""),
        "Prio" to Regex("""\bPRIO\b"""),
        "Moeve" to Regex("""\bMOEVE\b"""),
        "Cepsa" to Regex("""\bCEPSA\b"""),
        "Intermarché" to Regex("""\bINTERMARCHE\b|\bITMP\b"""),
        "E.Leclerc" to Regex("""\bE\.?\s?LECLERC\b|\bLECLERC\b"""),
        "Auchan" to Regex("""\bAUCHAN\b|\bJUMBO\b"""),
        "Continente" to Regex("""\bCONTINENTE\b"""),
        "Pingo Doce" to Regex("""\bPINGO DOCE\b"""),
        "Alves Bandeira" to Regex("""\bALVES BANDEIRA\b"""),
        "OZ Energia" to Regex("""\bOZ ENERGIA\b"""),
        "Petroprix" to Regex("""\bPETROPRIX\b"""),
        "Plenergy" to Regex("""\bPLENERGY\b"""),
        "Q8" to Regex("""\bQ8\b"""),
        "Shell" to Regex("""\bSHELL\b"""),
        "TotalEnergies" to Regex("""\bTOTAL ?ENERGIES\b"""),
        "Ecomarché" to Regex("""\bECOMARCHE\b"""),
    )

    /** Linhas que são empresa, morada ou contactos, não a identificação do posto. */
    private val COMPANY_OR_ADDRESS = Regex("""\bLDA\b|\bS\.?\s?A\.?$|\bS\.?A\.?\b|UNIPESSOAL|\bNIF\b|CONTRIBUINTE|CAP\.? ?SOC|C\.?R\.?C|\bTEL\b|\bTLF\b|\bFAX\b|\bAV\.?\b|\bAVENIDA\b|\bRUA\b|\bESTRADA\b|\bKM\b|\d{4}-\d{3}|WWW\.|@""")

    /** Palavras que não fazem parte do nome do local. */
    private val FILLER = setOf(
        "A.S.", "AS", "E.S.", "ES", "POSTO", "POSTOS", "AREA", "ESTACAO", "DE", "SERVICO", "SERVICOS",
        "ABASTECIMENTO", "COMBUSTIVEIS", "BOMBA", "BOMBAS", "-", "–", "/",
    )

    fun suggestName(rows: List<String>): String? {
        var bestBrand: String? = null
        var bestPlace = ""
        var bestRank = Int.MAX_VALUE
        for (row in rows) {
            // Palavras originais (com acentos, para o nome) e normalizadas (para comparar).
            val words = row.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val normalizedWords = words.map { normalize(it) }
            val line = normalizedWords.joinToString(" ")
            for ((index, entry) in BRANDS.withIndex()) {
                val (brand, regex) = entry
                val match = regex.find(line) ?: continue
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
        val brand = bestBrand ?: return null
        return if (bestPlace.isEmpty()) brand else "$brand $bestPlace"
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
