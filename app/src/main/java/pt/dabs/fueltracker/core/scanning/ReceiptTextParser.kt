package pt.dabs.fueltracker.core.scanning

import pt.dabs.fueltracker.core.domain.FuelMath
import pt.dabs.fueltracker.core.domain.NumberInput
import java.util.Locale

enum class AmountSource {
    /** Lido diretamente do texto do talão. */
    OCR,
    /** Calculado a partir do total do QR e do outro valor. */
    DERIVED,
    NONE,
}

/** Litros e preço/litro extraídos do texto de um talão. São sempre sugestões a confirmar. */
data class ReceiptAmounts(
    val volumeMl: Int?,
    val volumeSource: AmountSource,
    val pricePerLiterMilliCents: Int?,
    val priceSource: AmountSource,
    /** null sem total ou sem os dois valores; false quando litros × preço se afasta do total. */
    val consistentWithTotal: Boolean?,
) {
    companion object {
        val EMPTY = ReceiptAmounts(null, AmountSource.NONE, null, AmountSource.NONE, null)
    }
}

data class ReceiptParserOptions(
    val minVolumeMl: Int = 1_000,
    val maxVolumeMl: Int = 150_000,
    val minPriceMilliCents: Int = 50_000,   // 0,500 €/L
    val maxPriceMilliCents: Int = 350_000,  // 3,500 €/L
    /** Pontuação mínima para aceitar um valor sem confirmação aritmética pelo total. */
    val minStandaloneScore: Int = 2,
    val baseToleranceCents: Long = 3,
)

/**
 * Extrai litros e preço por litro do texto OCR de talões de combustível portugueses.
 *
 * Regras, por ordem de confiança:
 * 1. Um par (litros, preço) cujo produto bate com o total do QR.
 * 2. Litros e preço ambos com palavras-chave fortes, mesmo que não batam com o total
 *    (o talão pode incluir outros artigos); fica assinalado como inconsistente.
 * 3. Só litros ou só preço com palavra-chave; o outro deriva do total.
 */
object ReceiptTextParser {

    private val VOLUME_KEYWORDS = Regex("""\b(LITROS?|LTS?|QTD\.?|QUANT(IDADE)?\.?|VOLUME)\b""")
    private val VOLUME_UNIT_AFTER = Regex("""^\s?(L|LT|LTS)\b""")
    private val PRICE_KEYWORDS = Regex("""(€|EUR)\s?/\s?L|/\s?L\b|PRE[ÇC]O|P\.?\s?UNIT|\bPVP\b|\bUNIT""")
    private val FUEL_KEYWORDS = Regex("""GASOLINA|GAS[OÓ]LEO|DIESEL|SIMPLES|ADITIVAD|EFITEC|ULTIMATE|EXCELLIUM|EVOLOGIC|G-?FORCE|HI-?ENERGY|\bGPL\b|\b9[58]\b""")
    private val MONEY_BEFORE = Regex("""(€|EUR)\s?$""")
    private val MONEY_AFTER = Regex("""^\s?(€|EUR)(?!\s?/)""")

    /**
     * Números com 2 ou 3 decimais, tolerando O, I e L no lugar de 0 e 1 (as linhas já vêm em
     * maiúsculas). Não pode vir colado a uma letra à esquerda, para "TOTAL,12" não virar "1,12".
     */
    private val NUMBER = Regex("""(?<![\d.,A-Z])([0-9OIL]{1,4})[.,]([0-9OIL]{2,3})(?![\d.,])""")

    private data class Token(
        val row: Int,
        val start: Int,
        val end: Int,
        val integerPart: String,
        val fractionPart: String,
    ) {
        val decimals: Int get() = fractionPart.length
        val normalized: String get() = "$integerPart,$fractionPart"
        val asVolumeMl: Int? get() = NumberInput.parseScaled(normalized, 3)?.toInt()
        val asPriceMilliCents: Int? get() = NumberInput.parseScaled(normalized, 5)?.toInt()
        val asCents: Long? get() = NumberInput.parseScaled(normalized, 2)
    }

    private data class Candidate(val token: Token, val value: Int, var score: Int)

    fun parse(
        rows: List<String>,
        totalCents: Long?,
        options: ReceiptParserOptions = ReceiptParserOptions(),
    ): ReceiptAmounts {
        val normalizedRows = rows.map { normalizeRow(it) }
        val tokens = normalizedRows.flatMapIndexed { index, row -> tokensOf(index, row) }
        if (tokens.isEmpty()) return ReceiptAmounts.EMPTY

        val volumes = ArrayList<Candidate>()
        val prices = ArrayList<Candidate>()
        for (token in tokens) {
            val row = normalizedRows[token.row]
            val before = row.substring(0, token.start)
            val after = row.substring(token.end)
            val moneyMarked = MONEY_BEFORE.containsMatchIn(before) || MONEY_AFTER.containsMatchIn(after)
            val unitAfter = VOLUME_UNIT_AFTER.containsMatchIn(after)
            val equalsTotal = totalCents != null && token.asCents == totalCents
            val fuelRow = FUEL_KEYWORDS.containsMatchIn(row)

            token.asVolumeMl?.takeIf { it in options.minVolumeMl..options.maxVolumeMl }?.let { ml ->
                var score = 0
                if (VOLUME_KEYWORDS.containsMatchIn(row)) score += 2
                if (unitAfter) score += 2
                if (fuelRow) score += 1
                if (moneyMarked && !unitAfter) score -= 3
                if (equalsTotal && !unitAfter) score -= 5
                volumes += Candidate(token, ml, score)
            }
            token.asPriceMilliCents?.takeIf { it in options.minPriceMilliCents..options.maxPriceMilliCents }?.let { price ->
                var score = 0
                if (PRICE_KEYWORDS.containsMatchIn(row)) score += 2
                if (token.decimals == 3) score += 2
                if (fuelRow) score += 1
                if (unitAfter) score -= 2
                if (equalsTotal) score -= 5
                prices += Candidate(token, price, score)
            }
        }

        boostArithmeticTriples(tokens, volumes, prices, options)

        val tolerance = { total: Long -> options.baseToleranceCents + total / 1000 }

        // 1. Par confirmado pelo total do QR.
        if (totalCents != null) {
            val consistent = volumes.flatMap { v ->
                prices.filter { p -> p.token != v.token && matchesTotal(v, p, totalCents, tolerance(totalCents)) }
                    .map { p -> Triple(v, p, v.score + p.score) }
            }
            consistent.maxByOrNull { it.third }?.let { (v, p, _) ->
                return ReceiptAmounts(v.value, AmountSource.OCR, p.value, AmountSource.OCR, true)
            }
        }

        val bestVolume = volumes.filter { it.score >= options.minStandaloneScore }.maxByOrNull { it.score }
        val bestPrice = prices.filter { it.score >= options.minStandaloneScore && it.token != bestVolume?.token }.maxByOrNull { it.score }

        // 2. Ambos lidos com confiança, mas sem bater com o total (ex.: café no mesmo talão).
        if (bestVolume != null && bestPrice != null) {
            val consistent = totalCents?.let { total -> matchesTotal(bestVolume, bestPrice, total, tolerance(total)) }
            return ReceiptAmounts(bestVolume.value, AmountSource.OCR, bestPrice.value, AmountSource.OCR, consistent)
        }

        // 3. Só um dos valores; o outro deriva do total quando plausível.
        if (bestVolume != null) {
            val derived = totalCents?.let { FuelMath.pricePerLiterMilliCents(it, bestVolume.value) }
                ?.let { FuelMath.roundDiv(it.toLong(), 100).toInt() * 100 }
                ?.takeIf { it in options.minPriceMilliCents..options.maxPriceMilliCents }
            return ReceiptAmounts(
                bestVolume.value, AmountSource.OCR,
                derived, if (derived != null) AmountSource.DERIVED else AmountSource.NONE,
                if (derived != null) true else null,
            )
        }
        if (bestPrice != null) {
            val derived = totalCents?.let { FuelMath.volumeMl(it, bestPrice.value) }
                ?.takeIf { it in options.minVolumeMl..options.maxVolumeMl }
            return ReceiptAmounts(
                derived, if (derived != null) AmountSource.DERIVED else AmountSource.NONE,
                bestPrice.value, AmountSource.OCR,
                if (derived != null) true else null,
            )
        }
        return ReceiptAmounts.EMPTY
    }

    /** litros × preço ≈ total, dentro da tolerância. */
    private fun matchesTotal(volume: Candidate, price: Candidate, totalCents: Long, toleranceCents: Long): Boolean =
        kotlin.math.abs(FuelMath.totalCents(volume.value, price.value) - totalCents) <= toleranceCents

    /** Numa linha "40,12 x 1,749 = 70,17" (em qualquer ordem) reforça os dois fatores. */
    private fun boostArithmeticTriples(
        tokens: List<Token>,
        volumes: MutableList<Candidate>,
        prices: MutableList<Candidate>,
        options: ReceiptParserOptions,
    ) {
        for ((_, rowTokens) in tokens.groupBy { it.row }) {
            if (rowTokens.size < 3) continue
            for (volume in volumes.filter { it.token in rowTokens }) {
                for (price in prices.filter { it.token in rowTokens && it.token != volume.token }) {
                    val product = FuelMath.totalCents(volume.value, price.value)
                    val matchesAmount = rowTokens.any { t ->
                        t != volume.token && t != price.token &&
                            t.asCents?.let { kotlin.math.abs(it - product) <= options.baseToleranceCents } == true
                    }
                    if (matchesAmount) {
                        volume.score += 3
                        price.score += 3
                    }
                }
            }
        }
    }

    private fun normalizeRow(row: String): String =
        row.replace(' ', ' ').replace(' ', ' ').uppercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()

    private fun tokensOf(rowIndex: Int, row: String): List<Token> =
        NUMBER.findAll(row).mapNotNull { match ->
            // Exige pelo menos um algarismo real para não transformar palavras em números.
            if (match.value.none { it.isDigit() }) return@mapNotNull null
            Token(
                row = rowIndex,
                start = match.range.first,
                end = match.range.last + 1,
                integerPart = fixDigits(match.groupValues[1]),
                fractionPart = fixDigits(match.groupValues[2]),
            )
        }.toList()

    private fun fixDigits(text: String): String = text.map { c ->
        when (c) {
            'O' -> '0'
            'I', 'L' -> '1'
            else -> c
        }
    }.joinToString("")
}
