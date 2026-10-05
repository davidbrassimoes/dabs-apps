package dev.santavibes.tv

import android.content.res.AssetManager
import android.util.Log
import dev.santavibes.core.Compose
import dev.santavibes.core.Composed
import dev.santavibes.core.Profile
import dev.santavibes.core.SceneDef
import dev.santavibes.core.SceneFormat

/**
 * Scenes, shared GLSL and profiles from the APK assets (copied at build time from
 * scenes/ and shared/ — see app/build.gradle.kts). Read once at startup.
 */
class SceneLibrary(private val assets: AssetManager) {
    class Entry(val def: SceneDef, val frag: String)

    val header: String = read("shared/glsl/header.glsl")
    val outputFrag: String = read("shared/glsl/output.frag")

    /** Valid scenes, ordered like the authoring list: by style (first tag), then id. */
    val scenes: List<Entry> = (assets.list("scenes") ?: emptyArray()).mapNotNull { id ->
        try {
            Entry(SceneFormat.parseScene(read("scenes/$id/scene.json"), id), read("scenes/$id/scene.frag"))
        } catch (e: Exception) {
            Log.w(TAG, "skipping scene $id: ${e.message}")
            null
        }
    }.sortedWith(compareBy({ it.def.style }, { it.def.id }))

    fun profile(id: String): Profile = SceneFormat.parseProfile(read("shared/profiles/$id.json"))

    fun compose(e: Entry): Composed {
        val includes = Compose.collectIncludes(e.frag, { p -> runCatching { read("shared/glsl/$p") }.getOrNull() })
        return Compose.compose(header, e.def.params, e.frag, "scenes/${e.def.id}/scene.frag", includes)
    }

    private fun read(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        const val TAG = "SantaVibes"
    }
}
