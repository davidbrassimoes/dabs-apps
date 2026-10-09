package dev.santavibes.core

/**
 * What the control page changes (3.2) and the box keeps across restarts (3.4). Plain data,
 * validated against the scene list: anything unknown or out of range falls back to a safe
 * value instead of failing. Values hold numbers for float/int/bool params (bool as 0/1) and
 * "#rrggbb" for colour params. Changed only on user actions, never per frame.
 */
class ControlState(
    var sceneId: String,
    /** Author preset of the scene ("" = scene defaults). */
    var presetId: String = "",
    val values: MutableMap<String, Any> = LinkedHashMap(),
    /** 5 "#rrggbb" colours leading the palette rotation; null = the scene's (or preset's) own. */
    var palette: List<String>? = null,
    var reactive: Boolean = true,
    var source: String = "auto",
    var reactivity: Double = 0.7,
    var images: List<String> = ImageSources.ALL,
    var profile: String = "tv",
    /** null = the profile's own value. */
    var brightness: Double? = null,
    var temperature: Double? = null,
    var paletteRotate: Boolean = true,
    var rotation: Boolean = false,
    var rotationMin: Int = 10,
)

object Control {
    val SOURCES = listOf("auto", "pc", "phone", "mic", "none")
    val PROFILES = listOf("tv", "projector")
    val ROTATION_MINUTES = listOf(5, 10, 20, 30, 60)

    /** What a patch touched, so the host only redoes what changed. */
    enum class Part { SCENE, VALUES, LOOK, SIGNAL, IMAGES, GRADE, PALETTE, ROTATION }

    fun initial(scenes: List<SceneDef>, sceneId: String?): ControlState {
        val def = scenes.firstOrNull { it.id == sceneId } ?: scenes.first()
        return ControlState(def.id).also { resetValues(it, def, null) }
    }

    fun scene(scenes: List<SceneDef>, s: ControlState): SceneDef = scenes.firstOrNull { it.id == s.sceneId } ?: scenes.first()

    /** Scene defaults, then the preset's values; the preset's palette (or none). */
    fun resetValues(s: ControlState, def: SceneDef, preset: Preset?) {
        s.presetId = preset?.id ?: ""
        s.values.clear()
        for (p in def.params) s.values[p.id] = defaultValue(p)
        preset?.params?.forEach { (k, v) -> def.params.firstOrNull { it.id == k }?.let { p -> clampValue(p, v)?.let { s.values[k] = it } } }
        s.palette = preset?.palette?.map(::hex)
    }

    fun defaultValue(p: ParamDef): Any = p.defaultColor?.let(::hex) ?: p.default

    /** A valid value for `p` from JSON-ish input, or null when it cannot be one. */
    fun clampValue(p: ParamDef, v: Any?): Any? = when (p.type) {
        ParamType.COLOR -> (v as? String)?.takeIf { HEX.matches(it) }?.lowercase()
        ParamType.BOOL -> when (v) { is Boolean -> if (v) 1.0 else 0.0; is Number -> if (v.toDouble() != 0.0) 1.0 else 0.0; else -> null }
        ParamType.INT -> (v as? Number)?.toDouble()?.let { Math.rint(it.coerceIn(p.min, p.max)) }
        ParamType.FLOAT -> (v as? Number)?.toDouble()?.takeIf { !it.isNaN() }?.coerceIn(p.min, p.max)
    }

    /**
     * Apply a partial update from the control page. Keys: scene, preset, values, look, source,
     * reactivity, images, profile, brightness, temperature, paletteRotate, rotation, rotationMin.
     */
    fun patch(s: ControlState, p: Map<*, *>, scenes: List<SceneDef>): Set<Part> {
        val changed = LinkedHashSet<Part>()
        (p["scene"] as? String)?.let { id ->
            scenes.firstOrNull { it.id == id }?.let { def ->
                s.sceneId = def.id
                resetValues(s, def, null)
                changed += Part.SCENE
            }
        }
        if (p.containsKey("preset")) {
            val def = scene(scenes, s)
            val id = p["preset"] as? String ?: ""
            resetValues(s, def, def.presets.firstOrNull { it.id == id })
            changed += Part.VALUES
        }
        (p["values"] as? Map<*, *>)?.let { vals ->
            val def = scene(scenes, s)
            for ((k, v) in vals) {
                val pd = def.params.firstOrNull { it.id == k } ?: continue
                clampValue(pd, v)?.let { s.values[pd.id] = it; changed += Part.VALUES }
            }
        }
        (p["palette"] as? List<*>)?.let { l ->
            val hexes = l.mapNotNull { (it as? String)?.takeIf { h -> HEX.matches(h) }?.lowercase() }
            if (hexes.size == 5) { s.palette = hexes; changed += Part.VALUES }
        }
        when (p["look"]) { "reactive" -> { s.reactive = true; changed += Part.LOOK }; "current" -> { s.reactive = false; changed += Part.LOOK } }
        (p["source"] as? String)?.takeIf { it in SOURCES }?.let { s.source = it; changed += Part.SIGNAL }
        (p["reactivity"] as? Number)?.let { s.reactivity = it.toDouble().coerceIn(0.0, 1.0); changed += Part.SIGNAL }
        (p["images"] as? List<*>)?.let { l -> s.images = ImageSources.ALL.filter { it in l }; changed += Part.IMAGES }
        (p["profile"] as? String)?.takeIf { it in PROFILES }?.let { s.profile = it; s.brightness = null; s.temperature = null; changed += Part.GRADE }
        if (p.containsKey("brightness")) { s.brightness = (p["brightness"] as? Number)?.toDouble()?.coerceIn(0.2, 1.0); changed += Part.GRADE }
        if (p.containsKey("temperature")) { s.temperature = (p["temperature"] as? Number)?.toDouble()?.coerceIn(-1.0, 1.0); changed += Part.GRADE }
        (p["paletteRotate"] as? Boolean)?.let { s.paletteRotate = it; changed += Part.PALETTE }
        (p["rotation"] as? Boolean)?.let { s.rotation = it; changed += Part.ROTATION }
        (p["rotationMin"] as? Number)?.toInt()?.takeIf { it in ROTATION_MINUTES }?.let { s.rotationMin = it; changed += Part.ROTATION }
        return changed
    }

    fun toJson(s: ControlState): Map<String, Any?> = linkedMapOf(
        "scene" to s.sceneId, "preset" to s.presetId, "values" to s.values, "palette" to s.palette,
        "look" to if (s.reactive) "reactive" else "current", "source" to s.source, "reactivity" to s.reactivity,
        "images" to s.images, "profile" to s.profile, "brightness" to s.brightness, "temperature" to s.temperature,
        "paletteRotate" to s.paletteRotate, "rotation" to s.rotation, "rotationMin" to s.rotationMin,
    )

    /** Rebuild a saved state; whatever no longer fits the scenes (a removed scene or param) is dropped. */
    fun fromJson(o: Map<*, *>, scenes: List<SceneDef>): ControlState {
        val s = initial(scenes, o["scene"] as? String)
        val def = scene(scenes, s)
        val rest = LinkedHashMap<Any?, Any?>(o)
        rest.remove("scene")
        val preset = rest.remove("preset") as? String
        def.presets.firstOrNull { it.id == preset }?.let { resetValues(s, def, it) }
        patch(s, rest, scenes)
        return s
    }

    /** Base values packed as SceneSlot expects: params in order, 1 float each or 3 for a colour. */
    fun pack(def: SceneDef, values: Map<String, Any>): FloatArray {
        val out = FloatArray(def.params.sumOf { if (it.type == ParamType.COLOR) 3 else 1 })
        var o = 0
        for (p in def.params) {
            val v = values[p.id] ?: defaultValue(p)
            if (p.type == ParamType.COLOR) {
                val c = if (v is String) SceneFormat.hexToRgb(v) else p.defaultColor ?: FloatArray(3)
                System.arraycopy(c, 0, out, o, 3)
                o += 3
            } else {
                out[o++] = ((v as? Number)?.toDouble() ?: p.default).toFloat()
            }
        }
        return out
    }

    /** Effective profile: the base profile with the page's brightness and temperature. */
    fun grade(base: Profile, s: ControlState) = Profile(
        base.id, base.name, s.brightness?.toFloat() ?: base.brightness, base.contrast, base.saturation, base.gamma,
        base.blackFloor, s.temperature?.toFloat() ?: base.temperature, base.dither, base.renderScale, base.maxFps,
    )

    fun hex(c: FloatArray): String = "#%02x%02x%02x".format(
        Math.round(c[0].coerceIn(0f, 1f) * 255), Math.round(c[1].coerceIn(0f, 1f) * 255), Math.round(c[2].coerceIn(0f, 1f) * 255),
    )

    private val HEX = Regex("^#[0-9a-fA-F]{6}$")
}
