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

/** Valores extraídos do texto de um talão. São sempre sugestões a confirmar. */
data class ReceiptAmounts(
    val volumeMl: Int?,
    val volumeSource: AmountSource,
    val pricePerLiterMilliCents: Int?,
    val priceSource: AmountSource,
    /**
     * null sem total ou sem os dois valores; false quando litros × preço se afasta do total
     * mais os descontos.
     */
    val consistentWithTotal: Boolean?,
    /** Soma dos descontos impressos no talão (positiva); 0 quando não há. */
    val discountCents: Long = 0,
    /** Total impresso na linha "Total" do talão, se lido. */
    val printedTotalCents: Long? = null,
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
 * Extrai litros, preço por litro, descontos e total do texto OCR de talões de combustível
 * portugueses.
 *
 * Os descontos (ex.: "Desconto Imediato -2,13 €") baixam o total do documento (o do QR), mas não
 * o preço por litro: litros × preço = total + descontos.
 *
 * Regras, por ordem de confiança:
 * 1. Um par (litros, preço) cujo produto bate com o total (do QR ou impresso) mais os descontos.
 * 2. Litros e preço ambos com palavras-chave fortes, mesmo que não batam com o total
 *    (o talão pode incluir outros artigos); fica assinalado como inconsistente.
 * 3. Só litros ou só preço com palavra-chave; o outro deriva do total mais os descontos.
 */
object ReceiptTextParser {

    private val VOLUME_KEYWORDS = Regex("""\b(LITROS?|LTS?|QTD\.?|QUANT(IDADE)?\.?|VOLUME)\b""")
    private val VOLUME_UNIT_AFTER = Regex("""^\s?(L|LT|LTS)\b""")
    /** "€/L", "EUR/LT", e também "E/LTR": o OCR lê muitas vezes o "€" como "e". */
    private val PRICE_UNIT_AFTER = Regex("""^\s?(€|EUR|E)\s?/\s?L""")
    private val PRICE_KEYWORDS = Regex("""(€|EUR|\bE)\s?/\s?L|/\s?L\b|PRE[ÇC]O|P\.?\s?UNIT|\bPVP\b|\bUNIT""")
    private val FUEL_KEYWORDS = Regex("""GASOLINA|GAS[OÓ]LEO|DIESEL|SIMPLES|ADITIVAD|EFITEC|ULTIMATE|EXCELLIUM|EVOLOGIC|G-?FORCE|HI-?ENERGY|\bGPL\b|\b9[58]\b""")
    /**
     * Quadros de impostos ("ISP 0,583 €/Lt 42,63 24,85 €", "IVA 23% 77,95 17,93") têm o mesmo
     * padrão quantidade × taxa = valor da linha do combustível e não podem ganhar-lhe.
     */
    private val TAX_ROW = Regex("""\bISP\b|\bIVA\b|\bTAXA\b|\bBASE\b|\bIMPOSTO""")
    private val DISCOUNT_ROW = Regex("""\bDESC(ONTOS?|\.)?(?![A-Z])|\bPROMO|POUPAN[ÇC]A|\bCUP[AÃ]O|\bVALE\b|\bB[OÓ]NUS""")
    /** Linha do total do documento: começa por "TOTAL" e não é cabeçalho nem quadro de impostos. */
    private val TOTAL_ROW = Regex("""^TOTAL\b(?!.*\b(IVA|ISP|BASE)\b)""")
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

    /**
     * @param totalCents total do documento (campo O do QR, já com os descontos do talão); sem QR
     * usa-se o total impresso, se for lido.
     */
    fun parse(
        rows: List<String>,
        totalCents: Long?,
        options: ReceiptParserOptions = ReceiptParserOptions(),
    ): ReceiptAmounts {
        val normalizedRows = rows.map { normalizeRow(it) }
        val tokens = normalizedRows.flatMapIndexed { index, row -> tokensOf(index, row) }
        if (tokens.isEmpty()) return ReceiptAmounts.EMPTY

        val discountCents = discountCents(normalizedRows, tokens)
        val printedTotal = printedTotalCents(normalizedRows, tokens)
        val total = totalCents ?: printedTotal
        // O que litros × preço deve dar: o total antes dos descontos.
        val grossCents = total?.plus(discountCents)

        val volumes = ArrayList<Candidate>()
        val prices = ArrayList<Candidate>()
        for (token in tokens) {
            val row = normalizedRows[token.row]
            if (DISCOUNT_ROW.containsMatchIn(row)) continue
            val before = row.substring(0, token.start)
            val after = row.substring(token.end)
            val moneyMarked = MONEY_BEFORE.containsMatchIn(before) || MONEY_AFTER.containsMatchIn(after)
            val unitAfter = VOLUME_UNIT_AFTER.containsMatchIn(after)
            val priceUnitAfter = PRICE_UNIT_AFTER.containsMatchIn(after)
            val equalsTotal = token.asCents?.let { it == total || it == grossCents || it == printedTotal } == true
            val fuelRow = FUEL_KEYWORDS.containsMatchIn(row)
            val taxRow = TAX_ROW.containsMatchIn(row)

            token.asVolumeMl?.takeIf { it in options.minVolumeMl..options.maxVolumeMl }?.let { ml ->
                var score = 0
                if (VOLUME_KEYWORDS.containsMatchIn(row)) score += 2
                if (unitAfter) score += 2
                if (fuelRow) score += 1
                if (moneyMarked && !unitAfter) score -= 3
                if (equalsTotal && !unitAfter) score -= 5
                if (priceUnitAfter) score -= 3
                if (taxRow) score -= 4
                volumes += Candidate(token, ml, score)
            }
            token.asPriceMilliCents?.takeIf { it in options.minPriceMilliCents..options.maxPriceMilliCents }?.let { price ->
                var score = 0
                if (PRICE_KEYWORDS.containsMatchIn(row)) score += 2
                if (token.decimals == 3) score += 2
                if (fuelRow) score += 1
                if (unitAfter) score -= 2
                if (equalsTotal) score -= 5
                if (taxRow) score -= 6
                prices += Candidate(token, price, score)
            }
        }

        boostArithmeticTriples(tokens, volumes, prices, options)

        val tolerance = { amount: Long -> options.baseToleranceCents + amount / 1000 }
        // Com desconto lido confirma-se contra o bruto; o total sozinho cobre um desconto mal lido.
        val targets = listOfNotNull(grossCents, total).distinct()
        fun matchesAnyTarget(v: Candidate, p: Candidate) = targets.any { matchesTotal(v, p, it, tolerance(it)) }
        fun result(volume: Int?, volumeSource: AmountSource, price: Int?, priceSource: AmountSource, consistent: Boolean?) =
            ReceiptAmounts(volume, volumeSource, price, priceSource, consistent, discountCents, printedTotal)

        // 1. Par confirmado pelo total.
        if (targets.isNotEmpty()) {
            val consistent = volumes.flatMap { v ->
                prices.filter { p -> p.token != v.token && matchesAnyTarget(v, p) }
                    .map { p -> Triple(v, p, v.score + p.score) }
            }
            consistent.maxByOrNull { it.third }?.let { (v, p, _) ->
                return result(v.value, AmountSource.OCR, p.value, AmountSource.OCR, true)
            }
        }

        val bestVolume = volumes.filter { it.score >= options.minStandaloneScore }.maxByOrNull { it.score }
        val bestPrice = prices.filter { it.score >= options.minStandaloneScore && it.token != bestVolume?.token }.maxByOrNull { it.score }

        // 2. Ambos lidos com confiança, mas sem bater com o total (ex.: café no mesmo talão).
        if (bestVolume != null && bestPrice != null) {
            val consistent = if (targets.isEmpty()) null else matchesAnyTarget(bestVolume, bestPrice)
            return result(bestVolume.value, AmountSource.OCR, bestPrice.value, AmountSource.OCR, consistent)
        }

        // 3. Só um dos valores; o outro deriva do total (antes dos descontos) quando plausível.
        if (bestVolume != null) {
            val derived = grossCents?.let { FuelMath.pricePerLiterMilliCents(it, bestVolume.value) }
                ?.let { FuelMath.roundDiv(it.toLong(), 100).toInt() * 100 }
                ?.takeIf { it in options.minPriceMilliCents..options.maxPriceMilliCents }
            return result(
                bestVolume.value, AmountSource.OCR,
                derived, if (derived != null) AmountSource.DERIVED else AmountSource.NONE,
                if (derived != null) true else null,
            )
        }
        if (bestPrice != null) {
            val derived = grossCents?.let { FuelMath.volumeMl(it, bestPrice.value) }
                ?.takeIf { it in options.minVolumeMl..options.maxVolumeMl }
            return result(
                derived, if (derived != null) AmountSource.DERIVED else AmountSource.NONE,
                bestPrice.value, AmountSource.OCR,
                if (derived != null) true else null,
            )
        }
        return result(null, AmountSource.NONE, null, AmountSource.NONE, null)
    }

    /** Soma dos valores (2 casas decimais) das linhas de desconto; ignora descontos por litro. */
    private fun discountCents(rows: List<String>, tokens: List<Token>): Long =
        rows.indices.filter { DISCOUNT_ROW.containsMatchIn(rows[it]) }.sumOf { index ->
            tokens.lastOrNull { it.row == index && it.decimals == 2 && !PRICE_UNIT_AFTER.containsMatchIn(rows[index].substring(it.end)) }
                ?.asCents ?: 0L
        }

    /** Valor da primeira linha "TOTAL ..." com um montante (2 casas decimais). */
    private fun printedTotalCents(rows: List<String>, tokens: List<Token>): Long? =
        rows.indices.firstNotNullOfOrNull { index ->
            if (!TOTAL_ROW.containsMatchIn(rows[index])) return@firstNotNullOfOrNull null
            tokens.lastOrNull { it.row == index && it.decimals == 2 }?.asCents?.takeIf { it > 0 }
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
