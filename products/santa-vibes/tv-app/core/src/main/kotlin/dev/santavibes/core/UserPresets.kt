package dev.santavibes.core

/**
 * The user's presets (3.3, decision 16): kept on the box, made from the control page. One keeps
 * the scene, its values, the palette, the look and the image sources; signal and output profile
 * stay out (they belong to the room, not to the visual). Exported as JSON ready for the
 * `presets` of the scene's scene.json, plus a `scene` field saying which one.
 */
class UserPreset(
    val id: String,
    val name: String,
    val scene: String,
    val values: Map<String, Any>,
    val palette: List<String>,
    val reactive: Boolean,
    val images: List<String>,
)

object UserPresets {
    const val MAX_NAME = 40

    /** A new preset from the current state; the id is the name as kebab-case, unique among `existing`. */
    fun fromState(s: ControlState, def: SceneDef, name: String, existing: List<UserPreset>): UserPreset {
        val clean = name.trim().take(MAX_NAME).ifEmpty { "Preset" }
        val base = slug(clean)
        var id = base
        var n = 2
        while (existing.any { it.id == id }) id = "$base-${n++}"
        val palette = s.palette ?: (def.presets.firstOrNull { it.id == s.presetId }?.palette ?: def.defaultPalette)?.map(Control::hex)
            ?: List(5) { "#000000" }
        return UserPreset(id, clean, def.id, LinkedHashMap(s.values), palette, s.reactive, s.images)
    }

    /** Put the preset on screen: scene, values over the scene defaults, palette, look and images. */
    fun applyTo(p: UserPreset, s: ControlState, scenes: List<SceneDef>): Boolean {
        val def = scenes.firstOrNull { it.id == p.scene } ?: return false
        s.sceneId = def.id
        Control.resetValues(s, def, null)
        for ((k, v) in p.values) def.params.firstOrNull { it.id == k }?.let { pd -> Control.clampValue(pd, v)?.let { s.values[k] = it } }
        s.palette = p.palette
        s.reactive = p.reactive
        s.images = ImageSources.ALL.filter { it in p.images }
        return true
    }

    /** The scene.json preset: only the params that differ from the scene defaults (the format's rule). */
    fun export(p: UserPreset, def: SceneDef): Map<String, Any?> {
        val params = LinkedHashMap<String, Any?>()
        for (pd in def.params) {
            if (pd.id == "react") continue // forced by the host, never part of a preset
            val v = p.values[pd.id] ?: continue
            if (v == Control.defaultValue(pd)) continue
            params[pd.id] = if (pd.type == ParamType.BOOL) v == 1.0 else v
        }
        return linkedMapOf("scene" to p.scene, "id" to p.id, "name" to p.name, "params" to params, "palette" to p.palette)
    }

    fun toJson(list: List<UserPreset>): List<Map<String, Any?>> = list.map {
        linkedMapOf(
            "id" to it.id, "name" to it.name, "scene" to it.scene, "values" to it.values, "palette" to it.palette,
            "look" to if (it.reactive) "reactive" else "current", "images" to it.images,
        )
    }

    /** Presets saved on the box; entries that cannot be read are skipped. */
    fun fromJson(list: List<*>): List<UserPreset> = list.mapNotNull { o ->
        o as? Map<*, *> ?: return@mapNotNull null
        val id = o["id"] as? String ?: return@mapNotNull null
        val values = LinkedHashMap<String, Any>()
        (o["values"] as? Map<*, *>)?.forEach { (k, v) -> if (k is String && v != null) values[k] = v }
        UserPreset(
            id, o["name"] as? String ?: id, o["scene"] as? String ?: return@mapNotNull null, values,
            (o["palette"] as? List<*>)?.filterIsInstance<String>()?.takeIf { it.size == 5 } ?: List(5) { "#000000" },
            o["look"] != "current", (o["images"] as? List<*>)?.filterIsInstance<String>() ?: ImageSources.ALL,
        )
    }

    fun slug(name: String): String {
        val n = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return n.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "preset" }
    }

    /** Automatic rotation (3.3): through the user's presets when there are at least two, else through the scenes. */
    fun rotatesPresets(count: Int) = count >= 2
}
