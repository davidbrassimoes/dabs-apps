package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Suppress("UNCHECKED_CAST")
class DriftTest {
    private val cases = Json.parse(File(REPO, "authoring/test/fixtures/drift-cases.json").readText()) as Map<String, Any?>
    private val eps = 1e-9

    @Test
    fun specsMatchTheSharedFixtures() {
        for (c in cases["specs"] as List<Map<String, Any?>>) {
            val scene = c["scene"] as String
            val param = c["param"] as String
            assertEquals("$scene/$param hash", (c["hash"] as Double).toLong(), Drift.fnv1a("$scene/$param"))
            val s = Drift.spec(scene, param)
            val want = c["spec"] as Map<String, Double>
            assertEquals("f1", want["f1"]!!, s.f1, eps)
            assertEquals("f2", want["f2"]!!, s.f2, eps)
            assertEquals("ph1", want["ph1"]!!, s.ph1, eps)
            assertEquals("ph2", want["ph2"]!!, s.ph2, eps)
            for (o in c["offsets"] as List<List<Double>>) assertEquals("$scene/$param offset(${o[0]})", o[1], Drift.offset(s, o[0]), eps)
        }
    }

    @Test
    fun valuesMatchTheSharedFixtures() {
        for (c in cases["values"] as List<Map<String, Any?>>) {
            val p = SceneFormat.param(c["param"] as Map<String, Any?>)
            val s = Drift.spec(c["scene"] as String, p.id)
            for (v in c["values"] as List<List<Double>>) {
                assertEquals("${c["name"]} value(${v[0]})", v[1], Drift.value(p, c["base"] as Double, s, v[0], c["amount"] as Double), eps)
            }
        }
    }

    @Test
    fun fnv1aMatchesThePublishedVectors() {
        assertEquals(0x811c9dc5L, Drift.fnv1a(""))
        assertEquals(0xe40c292cL, Drift.fnv1a("a"))
        assertEquals(0xbf9cf968L, Drift.fnv1a("foobar"))
    }

    @Test
    fun boolAndColorNeverDrift() {
        val b = SceneFormat.param(mapOf("id" to "b", "type" to "bool", "default" to true, "drift" to 1.0))
        assertEquals(1.0, Drift.value(b, 1.0, Drift.spec("x", "b"), 12.0, 1.0), 0.0)
        val c = SceneFormat.param(mapOf("id" to "c", "type" to "color", "default" to "#ff0000", "drift" to 1.0))
        assertEquals(0.0, Drift.value(c, 0.0, Drift.spec("x", "c"), 12.0, 1.0), 0.0)
    }

    @Test
    fun everySceneParamDriftsInsideItsRange() {
        val scenes = File(REPO, "scenes").listFiles()!!.filter { File(it, "scene.json").isFile }
        assertTrue(scenes.size >= 15)
        for (dir in scenes) {
            val def = SceneFormat.parseScene(File(dir, "scene.json").readText(), dir.name)
            for (p in def.params) {
                if (p.type != ParamType.FLOAT && p.type != ParamType.INT) continue
                val s = Drift.spec(def.id, p.id)
                var t = 0.0
                while (t < 600) {
                    val v = Drift.value(p, p.default, s, t, 2.0)
                    assertTrue("${def.id}/${p.id} at $t: $v", v >= p.min - eps && v <= p.max + eps)
                    t += 7.3
                }
            }
        }
    }
}
