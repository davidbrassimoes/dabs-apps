package dev.santavibes.core

// Scene and profile model. Mirrors docs/FORMATO-CENA.md (format 4) and
// authoring/src/scene.ts. Parsed once at load time; nothing here runs per frame.

enum class ParamType { FLOAT, INT, BOOL, COLOR }

class ParamDef(
    val id: String,
    val label: String,
    val type: ParamType,
    val min: Double,
    val max: Double,
    /** float/int: the number; bool: 0/1; color: unused (see [defaultColor]). */
    val default: Double,
    val defaultColor: FloatArray?,
    val drift: Double,
    val group: String?,
)

class Preset(
    val id: String,
    val name: String,
    val params: Map<String, Any?>,
    /** 5 colours, sRGB 0..1, or null to keep the scene's default palette. */
    val palette: List<FloatArray>?,
)

class SceneDef(
    val format: Int,
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val fps: Int?,
    val renderScale: Double?,
    val feedback: Boolean,
    val image: String,
    val defaultPalette: List<FloatArray>?,
    val params: List<ParamDef>,
    val presets: List<Preset>,
) {
    /** First tag, by convention (FORMATO-CENA.md). */
    val style: String get() = tags.firstOrNull() ?: ""
}

class Profile(
    val id: String,
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val gamma: Float,
    val blackFloor: Float,
    val temperature: Float,
    val dither: Float,
    val renderScale: Double,
    val maxFps: Int,
)

object SceneFormat {
    val SUPPORTED = setOf(1, 2, 3, 4)
    private val FALLBACK_PALETTE = listOf("#101018", "#2a2a48", "#5a4a8a", "#c07a6a", "#f0d0a0")

    class InvalidScene(message: String) : RuntimeException(message)

    fun hexToRgb(hex: String): FloatArray {
        val h = hex.removePrefix("#")
        val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h
        val n = full.toIntOrNull(16) ?: return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(((n shr 16) and 255) / 255f, ((n shr 8) and 255) / 255f, (n and 255) / 255f)
    }

    /** The palette for a scene, optionally overridden by a preset: 15 floats, sRGB. */
    fun palette(scene: SceneDef, preset: Preset? = null): FloatArray {
        val src = preset?.palette ?: scene.defaultPalette ?: FALLBACK_PALETTE.map(::hexToRgb)
        val out = FloatArray(15)
        for (k in 0 until 5) {
            val c = src.getOrNull(k) ?: continue
            out[k * 3] = c[0]; out[k * 3 + 1] = c[1]; out[k * 3 + 2] = c[2]
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    fun parseScene(text: String, folderId: String): SceneDef {
        val o = Json.parse(text) as? Map<String, Any?> ?: throw InvalidScene("scene.json is not an object")
        val format = (o["format"] as? Double)?.toInt() ?: 0
        if (format !in SUPPORTED) throw InvalidScene("format must be one of $SUPPORTED (got ${o["format"]})")
        val id = o["id"] as? String ?: ""
        if (id != folderId) throw InvalidScene("id \"$id\" does not match folder \"$folderId\"")
        val name = o["name"] as? String ?: throw InvalidScene("missing name")
        val params = (o["params"] as? List<Any?> ?: throw InvalidScene("params must be an array")).map {
            param(it as? Map<String, Any?> ?: throw InvalidScene("param must be an object"))
        }
        val presets = (o["presets"] as? List<Any?>)?.map { pr ->
            val m = pr as Map<String, Any?>
            Preset(
                id = m["id"] as String,
                name = m["name"] as String,
                params = (m["params"] as? Map<String, Any?>) ?: emptyMap(),
                palette = (m["palette"] as? List<Any?>)?.map { hexToRgb(it as String) },
            )
        } ?: emptyList()
        if (presets.isNotEmpty() && format < 2) throw InvalidScene("presets need format 2")
        return SceneDef(
            format = format,
            id = id,
            name = name,
            description = o["description"] as? String ?: "",
            tags = (o["tags"] as? List<Any?>)?.map { it as String } ?: emptyList(),
            fps = (o["fps"] as? Double)?.toInt(),
            renderScale = o["renderScale"] as? Double,
            feedback = o["feedback"] == true,
            image = o["image"] as? String ?: "none",
            defaultPalette = (o["defaultPalette"] as? List<Any?>)?.takeIf { it.size == 5 }?.map { hexToRgb(it as String) },
            params = params,
            presets = presets,
        )
    }

    /** A param definition from its JSON object (scene.json "params" entry). */
    fun param(m: Map<String, Any?>): ParamDef {
        val id = m["id"] as? String ?: throw InvalidScene("param without id")
        if (!Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(id)) throw InvalidScene("param id \"$id\" is not a valid identifier")
        val type = when (m["type"]) {
            "float" -> ParamType.FLOAT
            "int" -> ParamType.INT
            "bool" -> ParamType.BOOL
            "color" -> ParamType.COLOR
            else -> throw InvalidScene("param \"$id\": unknown type \"${m["type"]}\"")
        }
        val def = m["default"]
        return ParamDef(
            id = id,
            label = m["label"] as? String ?: id,
            type = type,
            min = m["min"] as? Double ?: 0.0,
            max = m["max"] as? Double ?: 1.0,
            default = when (type) {
                ParamType.BOOL -> if (def == true || def == 1.0 || def == "true") 1.0 else 0.0
                ParamType.COLOR -> 0.0
                else -> def as? Double ?: 0.0
            },
            defaultColor = if (type == ParamType.COLOR) hexToRgb(def as? String ?: "#000000") else null,
            drift = m["drift"] as? Double ?: 0.0,
            group = m["group"] as? String,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseProfile(text: String): Profile {
        val o = Json.parse(text) as Map<String, Any?>
        fun f(k: String, d: Double) = (o[k] as? Double ?: d)
        return Profile(
            id = o["id"] as? String ?: "",
            name = o["name"] as? String ?: "",
            brightness = f("brightness", 1.0).toFloat(),
            contrast = f("contrast", 1.0).toFloat(),
            saturation = f("saturation", 1.0).toFloat(),
            gamma = f("gamma", 1.0).toFloat(),
            blackFloor = f("blackFloor", 0.0).toFloat(),
            temperature = f("temperature", 0.0).toFloat(),
            dither = f("dither", 1.0).toFloat(),
            renderScale = f("renderScale", 0.5),
            maxFps = f("maxFps", 30.0).toInt(),
        )
    }
}
