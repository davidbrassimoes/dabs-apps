package dev.santavibes.tv

import android.util.Log
import dev.santavibes.core.Control
import dev.santavibes.core.ControlState
import dev.santavibes.core.Json
import dev.santavibes.core.JsonOut
import dev.santavibes.core.SceneFormat
import dev.santavibes.core.UserPreset
import dev.santavibes.core.UserPresets
import java.io.File

/**
 * The one place that changes what is on screen (3.2–3.4): the remote and the control page both
 * go through here. Holds the [ControlState] and the user's presets, pushes changes to the
 * renderer, signal hub and images, runs the automatic rotation and keeps both on disk so the
 * box comes back as it was. Thread-safe (the HTTP thread and the UI thread both call in).
 */
class Controller(
    private val library: SceneLibrary,
    private val renderer: SceneRenderer,
    private val hub: SignalHub,
    private val images: ImageProvider?,
    dir: File,
) {
    private val scenes = library.scenes.map { it.def }
    private val stateFile = File(dir, "control-state.json")
    private val presetsFile = File(dir, "user-presets.json")
    private val profiles = Control.PROFILES.associateWith { library.profile(it) }

    private val lock = Any()
    private var s: ControlState = load()
    private val presets: MutableList<UserPreset> = loadPresets()
    private var paletteEpoch = 0
    private var paletteKey = ""
    private var rotationAt = 0L
    private var rotationIndex = -1

    /** The state as JSON for the page. */
    fun state(): Map<String, Any?> = synchronized(lock) { Control.toJson(s) }

    fun sceneId(): String = synchronized(lock) { s.sceneId }

    /** Brightness and temperature of the chosen profile, for the page's sliders when the state has none. */
    fun profileDefaults(): Map<String, Any?> = synchronized(lock) {
        val p = profiles[s.profile] ?: profiles.getValue("tv")
        mapOf("brightness" to p.brightness.toDouble(), "temperature" to p.temperature.toDouble())
    }

    /** Push everything (startup and new GL context). */
    fun applyAll(): Unit = synchronized(lock) { apply(Control.Part.entries.toSet()) }

    /** Command-line extras win over the saved state for this run (MainActivity). */
    fun override(patch: Map<String, Any?>): Unit = synchronized(lock) {
        if (patch.isNotEmpty()) Control.patch(s, patch, scenes)
    }

    fun patch(p: Map<*, *>): Map<String, Any?> = synchronized(lock) {
        val parts = Control.patch(s, p, scenes)
        if (parts.isNotEmpty()) {
            if (Control.Part.SCENE in parts) rotationAt = System.currentTimeMillis()
            apply(parts)
            save()
        }
        Control.toJson(s)
    }

    /** ◀ ▶ on the remote or the page. */
    fun step(delta: Int): Map<String, Any?> = synchronized(lock) {
        val i = scenes.indexOfFirst { it.id == s.sceneId }
        patch(mapOf("scene" to scenes[((i + delta) % scenes.size + scenes.size) % scenes.size].id))
    }

    fun toggleLook(): Map<String, Any?> = synchronized(lock) { patch(mapOf("look" to if (s.reactive) "current" else "reactive")) }

    // ------------------------------------------------------------ user presets (3.3)

    fun presets(): List<Map<String, Any?>> = synchronized(lock) { UserPresets.toJson(presets) }

    fun savePreset(name: String): Map<String, Any?> = synchronized(lock) {
        val p = UserPresets.fromState(s, Control.scene(scenes, s), name, presets)
        presets.add(0, p)
        savePresets()
        UserPresets.toJson(listOf(p))[0]
    }

    fun deletePreset(id: String): Boolean = synchronized(lock) {
        presets.removeAll { it.id == id }.also { if (it) savePresets() }
    }

    fun applyPreset(id: String): Boolean = synchronized(lock) {
        val p = presets.firstOrNull { it.id == id } ?: return false
        if (!UserPresets.applyTo(p, s, scenes)) return false
        rotationAt = System.currentTimeMillis()
        apply(Control.Part.entries.toSet())
        save()
        true
    }

    fun exportPreset(id: String): Map<String, Any?>? = synchronized(lock) {
        val p = presets.firstOrNull { it.id == id } ?: return null
        UserPresets.export(p, scenes.firstOrNull { it.id == p.scene } ?: return null)
    }

    /** Automatic rotation, called twice a second from the UI thread. */
    fun tick(nowMs: Long): Unit = synchronized(lock) {
        if (!s.rotation) return
        if (rotationAt == 0L) rotationAt = nowMs
        if (nowMs - rotationAt < s.rotationMin * 60_000L) return
        rotationAt = nowMs
        if (UserPresets.rotatesPresets(presets.size)) {
            rotationIndex = (rotationIndex + 1) % presets.size
            Log.i(SceneLibrary.TAG, "rotation: preset ${presets[rotationIndex].id}")
            applyPreset(presets[rotationIndex].id)
        } else {
            step(1)
        }
        Unit
    }

    // ------------------------------------------------------------ applying

    private fun apply(parts: Set<Control.Part>) {
        val def = Control.scene(scenes, s)
        if (Control.Part.LOOK in parts) renderer.react = if (s.reactive) 1f else 0f
        if (Control.Part.SIGNAL in parts) {
            hub.mode = SignalHub.parseMode(s.source)
            renderer.mixer.reactivity = s.reactivity
        }
        if (Control.Part.IMAGES in parts) {
            images?.sources = s.images.toSet()
            renderer.images?.enabled = s.images.isNotEmpty()
        }
        // A slider leaves the palettes alone; a new scene, lead palette or image choice rebuilds them.
        val key = "${s.sceneId}|${s.palette}|${s.images}"
        if (key != paletteKey) {
            paletteKey = key
            paletteEpoch++
        }
        val base = profiles[s.profile] ?: profiles.getValue("tv")
        renderer.control(
            SceneRenderer.Control(
                scenes.indexOf(def), Control.pack(def, s.values), s.palette?.map(SceneFormat::hexToRgb), s.paletteRotate,
                Control.grade(base, s), paletteEpoch,
            ),
        )
    }

    // ------------------------------------------------------------ persistence (3.4)

    private fun load(): ControlState = runCatching {
        Control.fromJson(Json.parse(stateFile.readText()) as Map<*, *>, scenes)
    }.getOrElse { Control.initial(scenes, null) }

    private fun loadPresets(): MutableList<UserPreset> = runCatching {
        UserPresets.fromJson(Json.parse(presetsFile.readText()) as List<*>).toMutableList()
    }.getOrElse { ArrayList() }

    private fun save() = write(stateFile, JsonOut.write(Control.toJson(s)))

    private fun savePresets() = write(presetsFile, JsonOut.write(UserPresets.toJson(presets)))

    /** Write to a temporary file and rename, so a power cut never leaves half a file. */
    private fun write(f: File, text: String) {
        try {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        } catch (e: Exception) {
            Log.w(SceneLibrary.TAG, "control: cannot save ${f.name}: $e")
        }
    }
}
