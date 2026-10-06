package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Suppress("UNCHECKED_CAST")
class PaletteVariationTest {
    @Test
    fun matchesTheSharedFixtures() {
        val f = Json.parse(File(REPO, "authoring/test/fixtures/palette-cases.json").readText()) as Map<String, Any?>
        val palettes = (f["palettes"] as List<List<String>>).map { p -> p.map(SceneFormat::hexToRgb) }
        for (c in f["cases"] as List<Map<String, Any?>>) {
            val st = c["settings"] as Map<String, Any?>
            val v = PaletteVariation().apply {
                rotate = st["rotate"] == true
                holdS = st["holdS"] as Double
                fadeS = st["fadeS"] as Double
                hueDeg = st["hueDeg"] as Double
            }
            v.setPalettes(f["scene"] as String, palettes, f["epoch"] as Double)
            val out = FloatArray(15)
            for (s in c["samples"] as List<Map<String, Any?>>) {
                val t = s["t"] as Double
                v.write(out, t)
                val name = "${c["name"]} at $t"
                assertEquals("$name current", (s["current"] as Double).toInt(), v.current)
                assertEquals("$name next", (s["next"] as Double).toInt(), v.next)
                assertEquals("$name mix", s["mix"] as Double, v.mix, 1e-12)
                (s["palette"] as List<Double>).forEachIndexed { i, x -> assertEquals("$name palette[$i]", x.toFloat(), out[i], 1e-6f) }
            }
        }
    }

    @Test
    fun oklabReferenceValuesAndRoundTrip() {
        val o = DoubleArray(3)
        PaletteVariation.srgbToOklab(1.0, 0.0, 0.0, o, 0) // published: L 0.6279, a 0.2249, b 0.1258
        assertEquals(0.6279, o[0], 1e-3)
        assertEquals(0.2249, o[1], 1e-3)
        assertEquals(0.1258, o[2], 1e-3)
        val back = FloatArray(3)
        for (c in listOf(doubleArrayOf(0.1, 0.5, 0.9), doubleArrayOf(0.95, 0.2, 0.4), doubleArrayOf(0.02, 0.03, 0.05))) {
            PaletteVariation.srgbToOklab(c[0], c[1], c[2], o, 0)
            PaletteVariation.oklabToSrgb(o[0], o[1], o[2], back, 0)
            for (i in 0..2) assertEquals(c[i].toFloat(), back[i], 1e-6f)
        }
    }

    @Test
    fun rotationPalettesPutsTheCurrentFirstAndDropsDuplicates() {
        val a = List(5) { floatArrayOf(0.1f * it, 0.2f, 0.3f) }
        val b = List(5) { floatArrayOf(0.5f, 0.1f * it, 0.3f) }
        val list = PaletteVariation.rotationPalettes(b, listOf(a, b.map { it.copyOf() }, a, emptyList()))
        assertEquals(2, list.size)
        assertSame(b, list[0])
    }

    @Test
    fun everySceneHasARotationSet() {
        val scenes = File(REPO, "scenes").listFiles()!!.filter { File(it, "scene.json").isFile }
        for (dir in scenes) {
            val def = SceneFormat.parseScene(File(dir, "scene.json").readText(), dir.name)
            val set = PaletteVariation.scenePalettes(def)
            assertTrue(def.id, set.isNotEmpty() && set.all { it.size == 5 })
        }
    }
}
