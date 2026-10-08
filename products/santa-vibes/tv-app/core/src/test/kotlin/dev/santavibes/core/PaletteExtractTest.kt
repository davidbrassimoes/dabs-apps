package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

/**
 * Palette extraction. authoring/test/fixtures/palette-extract-cases.json is the shared contract
 * (the authoring's twin must give the same colours); regenerate after a change with
 *   SV_WRITE_FIXTURES=1 ./gradlew :core:test --tests '*PaletteExtractTest*'
 */
class PaletteExtractTest {
    private val file = File(REPO, "authoring/test/fixtures/palette-extract-cases.json")

    private fun image(kind: Int): IntArray {
        var x = 0x9E3779B9.toInt()
        return IntArray(32 * 32) { i ->
            val u = (i % 32) / 31.0
            val v = (i / 32) / 31.0
            x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
            val n = ((x ushr 24) and 0xff) / 255.0
            val (r, g, b) = when (kind) {
                0 -> Triple(u, 0.3 + 0.4 * v, 1 - u) // smooth gradient
                1 -> if (u < 0.5) Triple(0.9, 0.2, 0.1) else Triple(0.1, 0.3, 0.8) // two flat halves
                2 -> Triple(0.2 + 0.6 * n, 0.5 * n, 0.3) // noisy warm
                else -> Triple(0.5 + 0.5 * sin(u * 9), 0.5 + 0.5 * sin(v * 7 + 1), 0.5 + 0.5 * sin((u + v) * 5)) // busy
            }
            fun ch(c: Double) = (c.coerceIn(0.0, 1.0) * 255).toInt()
            (0xff shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }
    }

    @Test
    fun matchesTheSharedFixture() {
        val sb = StringBuilder("{\n \"cases\": [\n")
        for (k in 0..3) {
            val px = image(k)
            val pal = PaletteExtract.extract(px)
            sb.append("  {\"w\": 32, \"h\": 32, \"rgb\": \"")
            for (p in px) sb.append(String.format("%06x", p and 0xffffff))
            sb.append("\", \"palette\": ").append(pal.joinToString(",", "[", "]")).append('}')
            sb.append(if (k < 3) ",\n" else "\n")
        }
        val text = sb.append(" ]\n}\n").toString()
        if (System.getenv("SV_WRITE_FIXTURES") == "1") file.writeText(text)
        assertEquals("regenerate with SV_WRITE_FIXTURES=1", file.readText().replace("\r\n", "\n"), text)
    }

    @Test
    fun twoFlatColoursBothSurvive() {
        val pal = PaletteExtract.extract(image(1))
        fun near(r: Float, g: Float, b: Float) = (0 until 5).any { k -> abs(pal[k * 3] - r) < 0.03 && abs(pal[k * 3 + 1] - g) < 0.03 && abs(pal[k * 3 + 2] - b) < 0.03 }
        assertTrue("red kept", near(0.898f, 0.196f, 0.098f))
        assertTrue("blue kept", near(0.098f, 0.294f, 0.796f))
    }

    @Test
    fun sortedDarkToLight() {
        for (k in 0..3) {
            val pal = PaletteExtract.extract(image(k))
            val l = DoubleArray(15)
            for (j in 0 until 5) PaletteVariation.srgbToOklab(pal[j * 3].toDouble(), pal[j * 3 + 1].toDouble(), pal[j * 3 + 2].toDouble(), l, j * 3)
            for (j in 1 until 5) assertTrue("case $k colour $j", l[j * 3] >= l[(j - 1) * 3] - 1e-6)
        }
    }
}
