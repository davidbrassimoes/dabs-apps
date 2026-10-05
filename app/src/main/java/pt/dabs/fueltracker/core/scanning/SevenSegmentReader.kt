package pt.dabs.fueltracker.core.scanning

import kotlin.math.abs

/**
 * Lê números de visores de sete segmentos (LCD/LED) numa imagem em tons de cinzento, sem OCR
 * genérico.
 *
 * O conta-km do carro (Opel Corsa D) desenha cada algarismo com blocos quadrados separados numa
 * grelha 5×7; o ML Kit, treinado para texto, não lê nada na foto inteira e confunde 6 com 5 no
 * recorte. Aqui:
 * 1. binariza em vários limiares (Otsu, Otsu só entre os píxeis claros, e uma varredura);
 * 2. junta componentes próximas (os blocos de um dígito, ou os segmentos de um visor contínuo);
 * 3. agrupa em linhas os glifos de altura e alinhamento parecidos;
 * 4. lê cada glifo pelos 7 segmentos acesos (um glifo estreito é um 1);
 * 5. marca as linhas com vírgula/ponto decimal (o conta-km parcial);
 * 6. vota: a leitura que aparece em mais limiares ganha (um segmento perdido num limiar não chega).
 *
 * Funções puras; testadas com amostras sintéticas e com a fotografia real do painel.
 */
object SevenSegmentReader {

    /** Número lido numa linha do visor. */
    data class SegmentNumber(
        val digits: String,
        /** Tem vírgula/ponto decimal (ex.: o parcial "602,0"); o conta-km total nunca tem. */
        val hasDecimal: Boolean,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        /** Em quantos limiares de binarização este número foi lido (mais = mais fiável). */
        val votes: Int = 1,
    )

    /** Lado maior a que a imagem é reduzida antes da análise (velocidade; o detalhe chega). */
    const val MAX_SIDE = 1_600
    private const val THRESHOLD_STEPS = 6
    private const val MIN_DIGIT_HEIGHT = 12
    private const val SEGMENT_ON = 0.30

    /** Candidatos a conta-km total (sem decimais, 4 a 7 algarismos), do mais votado para o menos. */
    fun readOdometerCandidates(image: LumaImage): List<Int> =
        odometerVotes(readNumbers(image)).map { it.first }

    /** O que se lê no painel: o conta-km total (em baixo) e o parcial (em cima, com uma casa decimal). */
    data class PanelReading(
        val totalKm: Int?,
        /** Parcial em décimas de km (602,0 km = 6020); o utilizador põe-no a zeros a cada abastecimento. */
        val tripTenthsKm: Int?,
        /** true/false quando há odómetro anterior e parcial: total ≈ anterior + parcial (±[TRIP_TOLERANCE_KM]). */
        val consistentWithTrip: Boolean?,
    ) {
        companion object {
            val EMPTY = PanelReading(null, null, null)
        }
    }

    /** Diferença admitida entre o total e anterior + parcial (o total não tem décimas e arredonda). */
    const val TRIP_TOLERANCE_KM = 2

    fun readOdometer(
        image: LumaImage,
        lastOdometerKm: Int?,
        maxDistanceKm: Int = OdometerReader.DEFAULT_MAX_DISTANCE_KM,
    ): Int? = readPanel(image, lastOdometerKm, maxDistanceKm).totalKm

    fun readPanel(
        image: LumaImage,
        lastOdometerKm: Int?,
        maxDistanceKm: Int = OdometerReader.DEFAULT_MAX_DISTANCE_KM,
    ): PanelReading = choosePanel(readNumbers(image), lastOdometerKm, maxDistanceKm)

    /**
     * Escolhe o total e o parcial entre os números lidos. O painel mostra sempre os dois: o parcial
     * em cima, com vírgula, e o total em baixo, que é o maior. Como o parcial é posto a zeros a cada
     * abastecimento, ao abastecer total ≈ odómetro anterior + parcial.
     *
     * 1. Parcial: o número com vírgula mais votado.
     * 2. Totais: sem vírgula, 4 a 7 algarismos, plausíveis face a [lastOdometerKm], maiores do que o
     *    parcial e diferentes dele (o parcial lido sem a vírgula não conta).
     * 3. Com odómetro anterior e parcial, ganha o total que bate com anterior + parcial; senão, entre
     *    os bem votados (≥ metade dos votos do melhor), o maior.
     */
    internal fun choosePanel(numbers: List<SegmentNumber>, lastOdometerKm: Int?, maxDistanceKm: Int): PanelReading {
        val trip = numbers.filter { it.hasDecimal && it.digits.length in 2..6 }.maxByOrNull { it.votes }
        val tripTenths = trip?.digits?.toIntOrNull()
        val totals = odometerVotes(numbers)
            .filter { (value, _) -> OdometerReader.pickPlausible(listOf(value), lastOdometerKm, maxDistanceKm) != null }
            .filter { (value, _) -> value.toString() != trip?.digits && (tripTenths == null || value * 10L > tripTenths) }
        if (totals.isEmpty()) return PanelReading(null, tripTenths, null)

        val expectedTenths = if (lastOdometerKm != null && tripTenths != null) lastOdometerKm * 10L + tripTenths else null
        fun consistent(value: Int) = expectedTenths != null && abs(value * 10L - expectedTenths) <= TRIP_TOLERANCE_KM * 10L
        val matching = totals.filter { consistent(it.first) }.maxByOrNull { it.second }
        val total = matching?.first ?: run {
            val bestVotes = totals.maxOf { it.second }
            totals.filter { it.second * 2 >= bestVotes }.maxOf { it.first }
        }
        return PanelReading(total, tripTenths, if (expectedTenths == null) null else consistent(total))
    }

    private fun odometerVotes(numbers: List<SegmentNumber>): List<Pair<Int, Int>> =
        numbers.filter { !it.hasDecimal && it.digits.length in OdometerReader.MIN_DIGITS..OdometerReader.MAX_DIGITS }
            .mapNotNull { number -> number.digits.toIntOrNull()?.let { it to number.votes } }
            .groupBy({ it.first }, { it.second })
            .map { (value, votes) -> value to votes.sum() }
            .sortedByDescending { it.second }

    /**
     * Números com pelo menos 2 algarismos legíveis. A imagem é binarizada em vários limiares
     * (os dois de Otsu e uma varredura até ao branco: o fundo de um LCD aceso e os mostradores
     * à volta mudam muito o histograma); [SegmentNumber.votes] conta em quantos foi lido.
     */
    fun readNumbers(image: LumaImage): List<SegmentNumber> {
        val scaled = image.downscaleTo(MAX_SIDE)
        val histogram = IntArray(256)
        for (b in scaled.pixels) histogram[b.toInt() and 0xFF]++
        val first = otsu(histogram, 0)
        val second = otsu(histogram, first + 1)
        val sweep = (1..THRESHOLD_STEPS).map { first + (250 - first) * it / (THRESHOLD_STEPS + 1) }
        val thresholds = (listOf(first, second) + sweep).filter { it in 1..254 }.distinct()
        return thresholds.flatMap { threshold -> readAtThreshold(scaled, threshold) }
            .groupBy { it.digits to it.hasDecimal }
            .map { (_, readings) -> readings.first().copy(votes = readings.size) }
            .sortedByDescending { it.votes }
    }

    // --- binarização ---------------------------------------------------------------------

    /** Limiar de Otsu considerando só os níveis ≥ [from]. Píxel aceso = luminância > limiar. */
    internal fun otsu(histogram: IntArray, from: Int): Int {
        var total = 0L
        var sum = 0.0
        for (i in from until 256) {
            total += histogram[i]
            sum += i.toDouble() * histogram[i]
        }
        if (total == 0L) return 255
        var weightBelow = 0L
        var sumBelow = 0.0
        var best = from
        var bestVariance = -1.0
        for (t in from until 255) {
            weightBelow += histogram[t]
            if (weightBelow == 0L) continue
            val weightAbove = total - weightBelow
            if (weightAbove == 0L) break
            sumBelow += t.toDouble() * histogram[t]
            val meanBelow = sumBelow / weightBelow
            val meanAbove = (sum - sumBelow) / weightAbove
            val variance = weightBelow.toDouble() * weightAbove * (meanBelow - meanAbove) * (meanBelow - meanAbove)
            if (variance > bestVariance) {
                bestVariance = variance
                best = t
            }
        }
        return best
    }

    private class Binary(val width: Int, val height: Int, val on: BooleanArray) {
        operator fun get(x: Int, y: Int) = on[y * width + x]

        /** Só as linhas [top, bottom), com a largura toda. */
        fun rows(top: Int, bottom: Int): Binary =
            Binary(width, bottom - top, on.copyOfRange(top * width, bottom * width))

        /** Fração de píxeis acesos no retângulo [l, r) × [t, b). */
        fun fill(l: Int, t: Int, r: Int, b: Int): Double {
            val left = l.coerceIn(0, width)
            val right = r.coerceIn(left, width)
            val top = t.coerceIn(0, height)
            val bottom = b.coerceIn(top, height)
            val area = (right - left) * (bottom - top)
            if (area == 0) return 0.0
            var count = 0
            for (y in top until bottom) for (x in left until right) if (on[y * width + x]) count++
            return count.toDouble() / area
        }
    }

    // --- componentes e glifos ------------------------------------------------------------

    private data class Box(var left: Int, var top: Int, var right: Int, var bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
        fun union(other: Box) = Box(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
        /** Distância entre caixas (0 se se tocam), a maior das distâncias horizontal e vertical. */
        fun gapTo(other: Box): Int = maxOf(maxOf(other.left - right, left - other.right, 0), maxOf(other.top - bottom, top - other.bottom, 0))
    }

    /** Componentes ligadas (vizinhança 4) com a sua caixa. */
    private fun components(binary: Binary): List<Box> {
        val labels = IntArray(binary.width * binary.height)
        val boxes = ArrayList<Box>()
        val stack = IntArray(binary.width * binary.height)
        val maxArea = binary.width * binary.height / 4
        for (start in labels.indices) {
            if (!binary.on[start] || labels[start] != 0) continue
            val label = boxes.size + 1
            var size = 0
            var area = 0
            stack[size++] = start
            labels[start] = label
            val box = Box(start % binary.width, start / binary.width, start % binary.width + 1, start / binary.width + 1)
            while (size > 0) {
                val p = stack[--size]
                area++
                val x = p % binary.width
                val y = p / binary.width
                if (x < box.left) box.left = x
                if (x + 1 > box.right) box.right = x + 1
                if (y < box.top) box.top = y
                if (y + 1 > box.bottom) box.bottom = y + 1
                if (x > 0 && binary.on[p - 1] && labels[p - 1] == 0) { labels[p - 1] = label; stack[size++] = p - 1 }
                if (x < binary.width - 1 && binary.on[p + 1] && labels[p + 1] == 0) { labels[p + 1] = label; stack[size++] = p + 1 }
                val up = p - binary.width
                if (y > 0 && binary.on[up] && labels[up] == 0) { labels[up] = label; stack[size++] = up }
                val down = p + binary.width
                if (y < binary.height - 1 && binary.on[down] && labels[down] == 0) { labels[down] = label; stack[size++] = down }
            }
            boxes += box
            // Fundos acesos enormes não são dígitos; ruído de 1-2 píxeis também não.
            if (area < 3 || area > maxArea) boxes[boxes.lastIndex] = Box(0, 0, 0, 0)
        }
        return boxes.filter { it.width > 0 && it.height > 0 }
    }

    /**
     * Junta componentes cuja distância é pequena face ao seu tamanho: os blocos de um dígito
     * (separados por ~¼ de bloco) ficam juntos; dígitos vizinhos (≥ 1½ bloco) não.
     */
    private fun mergeIntoGlyphs(parts: List<Box>): List<Box> {
        val sorted = parts.sortedBy { it.left }
        val parent = IntArray(sorted.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var j = i
            while (parent[j] != r) { val n = parent[j]; parent[j] = r; j = n }
            return r
        }
        val maxThickness = sorted.maxOfOrNull { minOf(it.width, it.height) } ?: 0
        for (i in sorted.indices) {
            val a = sorted[i]
            val thicknessA = minOf(a.width, a.height)
            for (j in i + 1 until sorted.size) {
                val b = sorted[j]
                if (b.left > a.right + maxThickness) break
                val thickness = minOf(thicknessA, minOf(b.width, b.height))
                // Na mesma coluna (um por cima do outro) admite-se o intervalo de um bloco apagado:
                // o 1 e os lados do 0 têm o bloco do meio apagado neste tipo de visor.
                val stacked = minOf(a.right, b.right) - maxOf(a.left, b.left) >= 0.5 * minOf(a.width, b.width)
                val limit = (if (stacked) 1.5 else 0.6) * thickness + 1
                if (a.gapTo(b) <= limit) {
                    val ra = find(i)
                    val rb = find(j)
                    if (ra != rb) parent[rb] = ra
                }
            }
        }
        return sorted.indices.groupBy { find(it) }.values.map { members -> members.map { sorted[it] }.reduce(Box::union) }
    }

    // --- linhas ----------------------------------------------------------------------------

    /**
     * Duas passagens. A primeira acha as linhas, nem que seja aos bocados: um rótulo aceso perto dos
     * dígitos (o "km" do Corsa) pode colar-se a dois deles e partir a linha. A segunda volta a
     * segmentar só dentro da faixa horizontal de cada linha, onde o que está por cima ou por baixo
     * já não faz ponte; a faixa desce um pouco abaixo da base para apanhar a vírgula decimal.
     */
    private fun readAtThreshold(image: LumaImage, threshold: Int): List<SegmentNumber> {
        val on = BooleanArray(image.pixels.size) { (image.pixels[it].toInt() and 0xFF) > threshold }
        val binary = Binary(image.width, image.height, on)
        val rows = groupRows(digitLikeGlyphs(mergeIntoGlyphs(components(binary))))
        val bands = rows.map { row ->
            val height = row.map { it.height }.sorted()[row.size / 2]
            (row.minOf { it.top } - (height * 0.1).toInt()).coerceAtLeast(0) to
                (row.maxOf { it.bottom } + (height * 0.3).toInt()).coerceAtMost(image.height)
        }.distinct()
        return bands.flatMap { (top, bottom) ->
            val band = binary.rows(top, bottom)
            val glyphs = mergeIntoGlyphs(components(band))
            groupRows(digitLikeGlyphs(glyphs)).mapNotNull { row -> readRow(band, row, glyphs) }
        }
    }

    private fun digitLikeGlyphs(glyphs: List<Box>) = glyphs.filter { it.height >= MIN_DIGIT_HEIGHT && it.width <= it.height }

    /** Glifos com altura e alinhamento vertical parecidos, seguidos da esquerda para a direita. */
    private fun groupRows(glyphs: List<Box>): List<List<Box>> {
        val rows = ArrayList<List<Box>>()
        val used = HashSet<Box>()
        for (seed in glyphs.sortedByDescending { it.height }) {
            if (seed in used) continue
            val sameLine = glyphs.filter { g ->
                g !in used &&
                    g.height in (seed.height * 0.75).toInt()..(seed.height * 1.25).toInt() &&
                    // Só o topo: uma vírgula colada a um dígito desce-lhe a base.
                    abs(g.top - seed.top) <= seed.height * 0.25
            }.sortedBy { it.left }
            // Corta onde o espaço entre glifos é grande demais para serem do mesmo número. Um 1 ocupa
            // só a coluna da direita da sua célula: o espaço antes dele é quase um dígito inteiro.
            var current = ArrayList<Box>()
            for (g in sameLine) {
                val last = current.lastOrNull()
                val maxGap = seed.height * if (g.width < seed.height * NARROW_RATIO) 1.4 else 0.9
                if (last != null && g.left - last.right > maxGap) {
                    if (current.contains(seed)) break
                    current = ArrayList()
                }
                current += g
            }
            if (seed in current) {
                used += current
                rows += current
            } else {
                used += seed
            }
        }
        return rows
    }

    private fun readRow(binary: Binary, row: List<Box>, allGlyphs: List<Box>): SegmentNumber? {
        if (row.size < 2) return null
        val top = row.map { it.top }.sorted()[row.size / 2]
        val bottom = row.map { it.bottom }.sorted()[row.size / 2]
        val height = bottom - top
        val wideWidths = row.filter { it.width >= height * NARROW_RATIO }.map { it.width }.sorted()
        val digitWidth = if (wideWidths.isEmpty()) (height * 0.6).toInt() else wideWidths[wideWidths.size / 2]

        val digits = StringBuilder()
        var attachedDecimal = false
        for (glyph in row) {
            // Todos os dígitos ocupam a altura toda. Mede-se a partir do topo do próprio glifo
            // (numa fotografia em perspetiva a linha fica inclinada) com a altura mediana da linha:
            // uma vírgula colada ao dígito fica de fora, mas denuncia-se por descer abaixo da base.
            if (glyph.height < height * 0.8) return null
            if (glyph.height > height * 1.08) attachedDecimal = true
            val glyphBottom = glyph.top + height
            val digit = if (glyph.width < height * NARROW_RATIO) {
                // Um 1: coluna estreita acesa em quase toda a sua altura (pode faltar-lhe o bloco de baixo).
                if (binary.fill(glyph.left, glyph.top, glyph.right, glyph.bottom) >= 0.45) '1' else null
            } else {
                // Todos os dígitos exceto o 1 têm o lado direito: alinha a caixa pela direita.
                decode(binary, glyph.right - digitWidth, glyph.top, glyph.right, glyphBottom)
            }
            digits.append(digit ?: return null)
        }

        val rowLeft = row.first().left
        val rowRight = row.last().right
        val hasDecimal = attachedDecimal || allGlyphs.any { g ->
            g.height < height * 0.45 && g.width < height * 0.45 &&
                g.centerX in rowLeft..rowRight &&
                g.top >= top + height * 0.6 && g.top <= bottom + height * 0.1 &&
                row.none { it.left <= g.centerX && g.centerX <= it.right }
        }
        return SegmentNumber(digits.toString(), hasDecimal, rowLeft, top, rowRight, bottom)
    }

    // --- segmentos -------------------------------------------------------------------------

    private const val NARROW_RATIO = 0.3

    /** Zonas (frações da caixa do dígito: x0, y0, x1, y1) dos segmentos a..g. */
    private val SEGMENTS = arrayOf(
        doubleArrayOf(0.30, 0.00, 0.70, 0.14), // a: topo
        doubleArrayOf(0.80, 0.20, 1.00, 0.40), // b: direita em cima
        doubleArrayOf(0.80, 0.60, 1.00, 0.80), // c: direita em baixo
        doubleArrayOf(0.30, 0.86, 0.70, 1.00), // d: base
        doubleArrayOf(0.00, 0.60, 0.20, 0.80), // e: esquerda em baixo
        doubleArrayOf(0.00, 0.20, 0.20, 0.40), // f: esquerda em cima
        doubleArrayOf(0.30, 0.43, 0.70, 0.57), // g: meio
    )

    /** Padrões a..g (bit 0 = a) → dígito; inclui as variantes comuns do 6, 7 e 9. */
    private val PATTERNS: Map<Int, Char> = mapOf(
        "abcdef" to '0', "bc" to '1', "abdeg" to '2', "abcdg" to '3', "bcfg" to '4',
        "acdfg" to '5', "acdefg" to '6', "cdefg" to '6', "abc" to '7', "abcf" to '7',
        "abcdefg" to '8', "abcdfg" to '9', "abcfg" to '9',
    ).mapKeys { (segments, _) -> segments.fold(0) { acc, c -> acc or (1 shl (c - 'a')) } }

    private fun decode(binary: Binary, left: Int, top: Int, right: Int, bottom: Int): Char? {
        val w = right - left
        val h = bottom - top
        var mask = 0
        for ((index, zone) in SEGMENTS.withIndex()) {
            val fill = binary.fill(
                left + (zone[0] * w).toInt(), top + (zone[1] * h).toInt(),
                left + (zone[2] * w).toInt().coerceAtLeast((zone[0] * w).toInt() + 1),
                top + (zone[3] * h).toInt().coerceAtLeast((zone[1] * h).toInt() + 1),
            )
            if (fill >= SEGMENT_ON) mask = mask or (1 shl index)
        }
        return PATTERNS[mask]
    }
}
