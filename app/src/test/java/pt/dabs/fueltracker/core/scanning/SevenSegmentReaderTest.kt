package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.random.Random

class SevenSegmentReaderTest {

    // --- amostras reais ----------------------------------------------------------------

    private fun resource(name: String): LumaImage {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("odometer/$name")) { "sample $name missing" }
        val image: BufferedImage = stream.use { ImageIO.read(it) }
        val argb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        return LumaImage.fromArgb(image.width, image.height, argb)
    }

    @Test
    fun `real Corsa LCD crop reads the total and marks the trip as decimal`() {
        val numbers = SevenSegmentReader.readNumbers(resource("real_corsa_lcd.jpg"))
        assertTrue(numbers.toString(), numbers.any { it.digits == "162259" && !it.hasDecimal })
        assertTrue(numbers.toString(), numbers.any { it.digits == "6020" && it.hasDecimal })
        assertEquals(162_259, SevenSegmentReader.readOdometerCandidates(resource("real_corsa_lcd.jpg")).first())
        assertEquals(162_259, SevenSegmentReader.readOdometer(resource("real_corsa_lcd.jpg"), lastOdometerKm = null))
    }

    @Test
    fun `real Corsa dashboard photo finds the odometer among the gauges`() {
        val photo = resource("real_corsa_painel.jpg")
        assertEquals(162_259, SevenSegmentReader.readOdometer(photo, lastOdometerKm = 161_650))
        assertEquals(162_259, SevenSegmentReader.readOdometer(photo, lastOdometerKm = null))
        // Um odómetro anterior incompatível rejeita a leitura em vez de inventar outra.
        assertEquals(null, SevenSegmentReader.readOdometer(photo, lastOdometerKm = 170_000))
    }

    @Test
    fun `framing only the total digits, as the camera frame does, reads the odometer`() {
        // Zona à volta da linha do total na foto reduzida (1200 px de largura), com margem e um
        // pedaço do parcial e do "km" por cima, como acontece ao enquadrar à pressa.
        val frame = resource("real_corsa_painel.jpg").crop(400, 740, 800, 880)
        assertEquals(162_259, SevenSegmentReader.readOdometer(frame, lastOdometerKm = 161_650))
    }

    @Test
    fun `real Corsa panel reads total and trip, and checks them against the last fill up`() {
        val lcd = resource("real_corsa_lcd.jpg")
        val unknown = SevenSegmentReader.readPanel(lcd, lastOdometerKm = null)
        assertEquals(162_259, unknown.totalKm)
        assertEquals(6_020, unknown.tripTenthsKm)
        assertEquals(null, unknown.consistentWithTrip)
        // Parcial posto a zeros no abastecimento anterior, a 161 657 km: 161 657 + 602,0 = 162 259.
        val consistent = SevenSegmentReader.readPanel(lcd, lastOdometerKm = 161_657)
        assertEquals(162_259, consistent.totalKm)
        assertEquals(true, consistent.consistentWithTrip)
        // Parcial não posto a zeros da última vez: o total lido mantém-se, só a verificação falha.
        val forgotReset = SevenSegmentReader.readPanel(lcd, lastOdometerKm = 161_000)
        assertEquals(162_259, forgotReset.totalKm)
        assertEquals(false, forgotReset.consistentWithTrip)
    }

    // --- escolha entre os números lidos ----------------------------------------------------

    private fun number(digits: String, votes: Int, decimal: Boolean = false) =
        SevenSegmentReader.SegmentNumber(digits, decimal, 0, 0, 0, 0, votes)

    private fun choose(vararg numbers: SevenSegmentReader.SegmentNumber, last: Int? = null) =
        SevenSegmentReader.choosePanel(numbers.toList(), last, OdometerReader.DEFAULT_MAX_DISTANCE_KM)

    @Test
    fun `the larger value is the total, even when the trip loses its comma`() {
        val panel = choose(number("6020", votes = 5), number("162259", votes = 3))
        assertEquals(162_259, panel.totalKm)
    }

    @Test
    fun `the trip itself is never the total`() {
        // Com vírgula num limiar e sem ela noutro: o 6020 sem vírgula é o parcial, não um total.
        val panel = choose(number("6020", votes = 4, decimal = true), number("6020", votes = 2))
        assertEquals(null, panel.totalKm)
        assertEquals(6_020, panel.tripTenthsKm)
    }

    @Test
    fun `last odometer plus trip picks the right total among misreads`() {
        // 162 259 e uma leitura errada 162 289, ambas plausíveis; o parcial desempata.
        val panel = choose(
            number("6020", votes = 3, decimal = true),
            number("162289", votes = 3),
            number("162259", votes = 2),
            last = 161_657,
        )
        assertEquals(162_259, panel.totalKm)
        assertEquals(true, panel.consistentWithTrip)
    }

    @Test
    fun `poorly voted larger misreads do not win`() {
        val panel = choose(number("162259", votes = 6), number("762259", votes = 1))
        assertEquals(162_259, panel.totalKm)
    }

    // --- amostras sintéticas -----------------------------------------------------------

    private enum class Style { BLOCKS, SOLID }

    /** Segmentos de cada dígito (a..g). */
    private val digitSegments = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
    )

    /**
     * Desenha [text] (dígitos e ",") com dígitos de altura [h]. BLOCKS imita o visor do Corsa:
     * grelha 5×7 de blocos com intervalos; SOLID é um sete-segmentos contínuo.
     */
    private fun render(
        text: String,
        h: Int = 70,
        style: Style = Style.BLOCKS,
        background: Int = 100,
        foreground: Int = 210,
        surround: Int = 20,
        noise: Int = 12,
        seed: Int = 1,
    ): LumaImage {
        val cell = h / 7
        val digitWidth = cell * 5
        val pitch = digitWidth + cell * 2
        val margin = h
        val width = margin * 2 + text.length * pitch
        val height = margin * 2 + h + cell
        val px = IntArray(width * height) { surround }
        // Fundo do LCD aceso, mais claro do que o que está à volta.
        for (y in margin / 2 until height - margin / 2) for (x in margin / 2 until width - margin / 2) px[y * width + x] = background
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0 until y1) for (x in x0 until x1) px[y * width + x] = foreground
        }
        var x = margin
        for (c in text) {
            if (c == ',') {
                fill(x - cell * 2 + cell / 2, margin + h - cell / 2, x - cell, margin + h + cell)
                continue
            }
            val segments = digitSegments.getValue(c)
            if (style == Style.BLOCKS) {
                // Células da grelha acesas por cada segmento (cantos incluídos, como no visor real).
                val cells = HashSet<Pair<Int, Int>>()
                if ('a' in segments) (0..4).forEach { cells += it to 0 }
                if ('g' in segments) (0..4).forEach { cells += it to 3 }
                if ('d' in segments) (0..4).forEach { cells += it to 6 }
                if ('f' in segments) (0..3).forEach { cells += 0 to it }
                if ('b' in segments) (0..3).forEach { cells += 4 to it }
                if ('e' in segments) (3..6).forEach { cells += 0 to it }
                if ('c' in segments) (3..6).forEach { cells += 4 to it }
                if (c == '1') { cells.clear(); (0..6).forEach { cells += 4 to it } }
                val gap = maxOf(1, cell / 5)
                for ((cx, cy) in cells) fill(x + cx * cell, margin + cy * cell, x + (cx + 1) * cell - gap, margin + (cy + 1) * cell - gap)
            } else {
                val t = cell
                val mid = margin + h / 2
                if ('a' in segments) fill(x + t, margin, x + digitWidth - t, margin + t)
                if ('g' in segments) fill(x + t, mid - t / 2, x + digitWidth - t, mid + t / 2)
                if ('d' in segments) fill(x + t, margin + h - t, x + digitWidth - t, margin + h)
                if ('f' in segments) fill(x, margin + t / 2, x + t, mid - t / 2)
                if ('b' in segments) fill(x + digitWidth - t, margin + t / 2, x + digitWidth, mid - t / 2)
                if ('e' in segments) fill(x, mid + t / 2, x + t, margin + h - t / 2)
                if ('c' in segments) fill(x + digitWidth - t, mid + t / 2, x + digitWidth, margin + h - t / 2)
            }
            x += pitch
        }
        val random = Random(seed)
        val bytes = ByteArray(px.size) { i -> (px[i] + random.nextInt(-noise, noise + 1)).coerceIn(0, 255).toByte() }
        return LumaImage(width, height, bytes)
    }

    @Test
    fun `block display reads every digit`() {
        for (number in listOf("162259", "1234567", "890", "4711", "203040")) {
            val read = SevenSegmentReader.readNumbers(render(number)).map { it.digits }
            assertTrue("$number → $read", number in read)
        }
    }

    @Test
    fun `solid seven segment display reads every digit`() {
        for (number in listOf("162259", "1234567", "890", "4711")) {
            val read = SevenSegmentReader.readNumbers(render(number, style = Style.SOLID)).map { it.digits }
            assertTrue("$number → $read", number in read)
        }
    }

    @Test
    fun `small digits and strong noise still read`() {
        assertTrue(SevenSegmentReader.readNumbers(render("162259", h = 28, noise = 25)).any { it.digits == "162259" })
    }

    @Test
    fun `uneven lighting across the display still reads`() {
        val flat = render("162259", background = 90, foreground = 190)
        // Reflexo: soma um gradiente horizontal de 0 a +60 (o lado direito fica mais claro).
        val lit = ByteArray(flat.pixels.size) { i ->
            val x = i % flat.width
            ((flat.pixels[i].toInt() and 0xFF) + 60 * x / flat.width).coerceAtMost(255).toByte()
        }
        assertEquals(162_259, SevenSegmentReader.readOdometer(LumaImage(flat.width, flat.height, lit), lastOdometerKm = 161_000))
    }

    @Test
    fun `trip meter with a comma is not an odometer candidate`() {
        val image = render("602,0")
        val numbers = SevenSegmentReader.readNumbers(image)
        assertTrue(numbers.toString(), numbers.any { it.digits == "6020" && it.hasDecimal })
        assertTrue(SevenSegmentReader.readOdometerCandidates(image).isEmpty())
    }

    @Test
    fun `rotated camera frame is read after rotating back`() {
        val upright = render("162259")
        val sensor = upright.rotate(270) // como chega da câmara em retrato (rotationDegrees = 90)
        assertEquals(listOf(162_259), SevenSegmentReader.readOdometerCandidates(sensor.rotate(90)))
    }

    @Test
    fun `blank or uniform images read nothing`() {
        assertTrue(SevenSegmentReader.readNumbers(LumaImage(200, 100, ByteArray(200 * 100) { 90 })).isEmpty())
        assertTrue(SevenSegmentReader.readNumbers(render("")).isEmpty())
    }

    @Test
    fun `otsu splits a bimodal histogram between the modes`() {
        val histogram = IntArray(256).apply { this[40] = 1000; this[200] = 300 }
        val threshold = SevenSegmentReader.otsu(histogram, 0)
        assertTrue(threshold in 40 until 200)
        assertFalse(SevenSegmentReader.otsu(histogram, 201) < 201)
    }
}
