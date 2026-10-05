package pt.dabs.fueltracker.core.scanning

import pt.dabs.fueltracker.core.domain.model.FuelType
import java.text.Normalizer
import java.util.Locale

/**
 * Combustível comprado, lido do texto OCR do talão: o tipo (95, 98, gasóleo, GPL) e se é
 * aditivado. Ex.: "N. de Bomba 3 EFI 95 PREM" → Gasolina 95 aditivada; "GASOLINA 95 SIMPLES" →
 * Gasolina 95 simples. Quando o talão não diz se é aditivado, [ProductSuggestion.premium] fica null.
 */
object ReceiptProductParser {

    data class ProductSuggestion(val type: FuelType?, val premium: Boolean?)

    /** Linhas que falam do combustível (para não confundir "95" com outros números). */
    private val FUEL_CUE = Regex("""GASOLINA|GASOLEO|DIESEL|\bGPL\b|AUTOGAS|BOMBA|COMBUSTIVEL|\bSP ?9[58]\b|EFITEC|\bEFI\b|ULTIMATE|V-?POWER|G-?FORCE|NEOTECH|EXCELLIUM|EVOLOGIC|\bPRIO\b|SIMPLES|ADITIVAD|LITROS""")

    /** 95 ou 98 soltos, sem fazer parte de um número com decimais ("95,88") nem de outro maior. */
    private val OCTANE = Regex("""(?<![\d.,])(9[58])(?![\d.,]*\d)""")

    /** Nomes comerciais e abreviaturas de combustível aditivado. */
    private val PREMIUM = Regex("""ADITIVAD|ULTIMATE|V-?POWER|G-?FORCE|NEOTECH|EXCELLIUM|\bPREM(IUM)?\b|\bTOP\b|\bSTAR\b|\bPLUS\b""")
    private val SIMPLE = Regex("""\bSIMPLES\b|\bNORMAL\b""")

    fun suggest(rows: List<String>): ProductSuggestion {
        var type: FuelType? = null
        var premium: Boolean? = null
        for (row in rows.map { normalize(it) }) {
            if (!FUEL_CUE.containsMatchIn(row)) continue
            val rowType = when {
                Regex("""\bGPL\b|AUTOGAS""").containsMatchIn(row) -> FuelType.LPG
                Regex("""GASOLEO|DIESEL""").containsMatchIn(row) -> FuelType.DIESEL
                else -> when (OCTANE.find(row)?.value) {
                    "98" -> FuelType.GASOLINE_98
                    "95" -> FuelType.GASOLINE_95
                    else -> if ("GASOLINA" in row) FuelType.GASOLINE_95 else null
                }
            }
            if (rowType != null && type == null) type = rowType
            if (premium == null) {
                if (PREMIUM.containsMatchIn(row)) premium = true
                else if (SIMPLE.containsMatchIn(row)) premium = false
            }
            if (type != null && premium != null) break
        }
        return ProductSuggestion(type, premium)
    }

    private fun normalize(row: String): String =
        Normalizer.normalize(row, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
            .uppercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()
}
