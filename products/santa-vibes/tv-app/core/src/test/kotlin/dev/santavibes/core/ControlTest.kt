package dev.santavibes.core

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlTest {
    private val scenes = File(REPO, "scenes").listFiles()!!.filter { File(it, "scene.json").exists() }.sortedBy { it.name }
        .map { SceneFormat.parseScene(File(it, "scene.json").readText(), it.name) }

    /** A made-up scene with every param type (no real scene has bool or colour params today). */
    private val kinds = SceneFormat.parseScene(
        """{ "format": 4, "id": "kinds", "name": "Kinds", "params": [
            { "id": "f", "label": "F", "type": "float", "min": 0, "max": 2, "default": 1 },
            { "id": "c", "label": "C", "type": "color", "default": "#ff8000" },
            { "id": "i", "label": "I", "type": "int", "min": 1, "max": 5, "default": 3 },
            { "id": "b", "label": "B", "type": "bool", "default": false } ],
          "presets": [ { "id": "p", "name": "P", "params": { "b": true, "c": "#000000" } } ] }""",
        "kinds",
    )

    @Test
    fun initialStateUsesSceneDefaults() {
        val s = Control.initial(scenes, "nebula")
        assertEquals("nebula", s.sceneId)
        assertEquals(0.25, s.values["speed"])
        assertNull(s.palette)
        assertTrue(s.reactive)
        assertEquals(Control.initial(scenes, "gone").sceneId, scenes.first().id)
    }

    @Test
    fun presetSetsItsValuesAndPalette() {
        val s = Control.initial(scenes, "nebula")
        assertEquals(setOf(Control.Part.VALUES), Control.patch(s, mapOf("preset" to "fumo"), scenes))
        assertEquals(0.15, s.values["speed"])
        assertEquals(2.5 - 1.0, s.values["scale"]) // fumo: scale 1.5
        assertEquals("#1e2028", s.palette!![0])
        Control.patch(s, mapOf("preset" to ""), scenes)
        assertEquals(0.25, s.values["speed"])
        assertNull(s.palette)
    }

    @Test
    fun valuesAreClampedAndUnknownOnesIgnored() {
        val s = Control.initial(scenes, "nebula")
        Control.patch(s, mapOf("values" to mapOf("speed" to 9.0, "warp" to -1.0, "nope" to 1.0, "scale" to "x")), scenes)
        assertEquals(1.0, s.values["speed"])
        assertEquals(0.0, s.values["warp"])
        assertFalse(s.values.containsKey("nope"))
        assertEquals(2.5, s.values["scale"])
        val k = ControlState("kinds").also { Control.resetValues(it, kinds, null) }
        Control.patch(k, mapOf("values" to mapOf("i" to 4.6, "b" to true, "c" to "#ABCDEF")), listOf(kinds))
        assertEquals(5.0, k.values["i"])
        assertEquals(1.0, k.values["b"])
        assertEquals("#abcdef", k.values["c"])
        Control.patch(k, mapOf("values" to mapOf("c" to "red")), listOf(kinds))
        assertEquals("#abcdef", k.values["c"])
    }

    @Test
    fun sceneChangeResetsValuesAndOtherFieldsValidate() {
        val s = Control.initial(scenes, "nebula")
        Control.patch(s, mapOf("preset" to "fumo"), scenes)
        val parts = Control.patch(
            s,
            mapOf("scene" to "halo", "look" to "current", "source" to "pc", "reactivity" to 3, "images" to listOf("nasa", "x"),
                "profile" to "projector", "brightness" to 0.1, "rotation" to true, "rotationMin" to 7),
            scenes,
        )
        assertTrue(parts.containsAll(listOf(Control.Part.SCENE, Control.Part.LOOK, Control.Part.SIGNAL, Control.Part.IMAGES, Control.Part.GRADE, Control.Part.ROTATION)))
        assertEquals("halo", s.sceneId)
        assertEquals("", s.presetId)
        assertFalse(s.values.containsKey("warp"))
        assertFalse(s.reactive)
        assertEquals(1.0, s.reactivity, 0.0)
        assertEquals(listOf("nasa"), s.images)
        assertEquals(0.2, s.brightness!!, 0.0)
        assertEquals(10, s.rotationMin) // 7 is not offered
        Control.patch(s, mapOf("source" to "radio"), scenes)
        assertEquals("pc", s.source)
    }

    @Test
    fun savedStateRoundTrips() {
        val s = Control.initial(scenes, "nebula")
        Control.patch(s, mapOf("preset" to "carina", "values" to mapOf("speed" to 0.6), "look" to "current", "images" to emptyList<String>(), "temperature" to -0.3), scenes)
        val back = Control.fromJson(Json.parse(JsonOut.write(Control.toJson(s))) as Map<*, *>, scenes)
        assertEquals(JsonOut.write(Control.toJson(s)), JsonOut.write(Control.toJson(back)))
        assertEquals("carina", back.presetId)
        assertEquals(0.6, back.values["speed"])
    }

    @Test
    fun packFollowsParamOrderWithThreeFloatsPerColour() {
        val k = ControlState("kinds").also { Control.resetValues(it, kinds, kinds.presets[0]) }
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 3f, 1f), Control.pack(kinds, k.values), 1e-6f)
        val d = ControlState("kinds").also { Control.resetValues(it, kinds, null) }
        assertArrayEquals(floatArrayOf(1f, 1f, 128 / 255f, 0f, 3f, 0f), Control.pack(kinds, d.values), 1e-6f)
    }

    @Test
    fun gradeOverridesOnlyBrightnessAndTemperature() {
        val base = SceneFormat.parseProfile(File(REPO, "shared/profiles/tv.json").readText())
        val s = Control.initial(scenes, null)
        assertEquals(base.brightness, Control.grade(base, s).brightness, 0f)
        s.brightness = 0.5
        s.temperature = -0.4
        val g = Control.grade(base, s)
        assertEquals(0.5f, g.brightness, 0f)
        assertEquals(-0.4f, g.temperature, 0f)
        assertEquals(base.contrast, g.contrast, 0f)
    }

    @Test
    fun jsonOutWritesWhatJsonReads() {
        val v = linkedMapOf("s" to "a\"b\\c\n\u0001é", "n" to listOf(1.0, 2.5, -3, null, true), "e" to emptyMap<String, Any>())
        val text = JsonOut.write(v)
        assertEquals("""{"s":"a\"b\\c\n\u0001é","n":[1,2.5,-3,null,true],"e":{}}""", text)
        assertEquals(listOf(1.0, 2.5, -3.0, null, true), (Json.parse(text) as Map<*, *>)["n"])
    }
}
