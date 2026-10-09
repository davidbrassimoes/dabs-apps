package dev.santavibes.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPresetsTest {
    private val scenes = File(REPO, "scenes").listFiles()!!.filter { File(it, "scene.json").exists() }.sortedBy { it.name }
        .map { SceneFormat.parseScene(File(it, "scene.json").readText(), it.name) }
    private val nebula = scenes.first { it.id == "nebula" }

    private fun state() = Control.initial(scenes, "nebula").also {
        Control.patch(it, mapOf("preset" to "fumo", "values" to mapOf("warp" to 2.4), "look" to "current", "images" to listOf("met")), scenes)
    }

    @Test
    fun savesSceneValuesPaletteLookAndImages() {
        val p = UserPresets.fromState(state(), nebula, "  Serão de Inverno ", emptyList())
        assertEquals("serao-de-inverno", p.id)
        assertEquals("Serão de Inverno", p.name)
        assertEquals("nebula", p.scene)
        assertEquals(2.4, p.values["warp"])
        assertEquals("#1e2028", p.palette[0]) // fumo's palette
        assertFalse(p.reactive)
        assertEquals(listOf("met"), p.images)
        val again = UserPresets.fromState(state(), nebula, "Serão de inverno", listOf(p))
        assertEquals("serao-de-inverno-2", again.id)
        assertEquals(nebula.defaultPalette!!.map(Control::hex), UserPresets.fromState(Control.initial(scenes, "nebula"), nebula, "x", emptyList()).palette)
    }

    @Test
    fun applyingRestoresTheVisual() {
        val p = UserPresets.fromState(state(), nebula, "Serão", emptyList())
        val s = Control.initial(scenes, "halo")
        assertTrue(UserPresets.applyTo(p, s, scenes))
        assertEquals("nebula", s.sceneId)
        assertEquals(2.4, s.values["warp"])
        assertEquals(0.15, s.values["speed"])
        assertEquals(p.palette, s.palette)
        assertFalse(s.reactive)
        assertEquals(listOf("met"), s.images)
        assertFalse(UserPresets.applyTo(UserPreset("x", "x", "gone", emptyMap(), p.palette, true, emptyList()), s, scenes))
    }

    @Test
    fun exportKeepsOnlyWhatDiffersFromTheDefaults() {
        val p = UserPresets.fromState(state(), nebula, "Serão", emptyList())
        val e = UserPresets.export(p, nebula)
        assertEquals("nebula", e["scene"])
        assertEquals("serao", e["id"])
        assertEquals(mapOf("speed" to 0.15, "scale" to 1.5, "warp" to 2.4), e["params"])
        assertEquals(p.palette, e["palette"])
    }

    @Test
    fun savedListRoundTripsAndSkipsBrokenEntries() {
        val p = UserPresets.fromState(state(), nebula, "Serão", emptyList())
        val text = JsonOut.write(UserPresets.toJson(listOf(p)))
        val back = UserPresets.fromJson(Json.parse("[" + text.removePrefix("[").removeSuffix("]") + ",{\"name\":\"no id\"},3]") as List<*>)
        assertEquals(1, back.size)
        assertEquals(text, JsonOut.write(UserPresets.toJson(back)))
    }

    @Test
    fun rotationNeedsTwoPresets() {
        assertFalse(UserPresets.rotatesPresets(1))
        assertTrue(UserPresets.rotatesPresets(2))
    }
}
