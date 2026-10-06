package dev.santavibes.core

import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Host-side palette variation — port of authoring/src/palette.ts (docs/ARQUITETURA.md,
 * "Variação de paleta"). Scenes keep reading uPalette only. Rotation through the scene's
 * palettes and hue drift, both in OKLab. [write] runs every frame and allocates nothing.
 */
class PaletteVariation {
    var rotate = false
    var holdS = 180.0 // seconds on each palette
    var fadeS = 20.0 // crossfade seconds
    var hueDeg = 0.0 // 0 = no hue drift

    /** Index of the palette shown (or being faded from) at the last write(); for the overlay. */
    var current = 0; private set
    var next = 0; private set
    var mix = 0.0; private set

    private var lab = DoubleArray(15) // OKLab of every palette, 15 numbers each
    private var count = 0
    private var epoch = 0.0
    private var hueSpec = Drift.spec("", "@palette")

    val size: Int get() = count

    /** Palettes to rotate through (first = current), each 5 sRGB colours; `t` is the drift time now. */
    fun setPalettes(sceneId: String, palettes: List<List<FloatArray>>, t: Double) {
        count = palettes.size
        if (lab.size < count * 15) lab = DoubleArray(count * 15)
        palettes.forEachIndexed { i, pal ->
            for (k in 0 until 5) {
                val c = pal.getOrNull(k)
                srgbToOklab(c?.get(0)?.toDouble() ?: 0.0, c?.get(1)?.toDouble() ?: 0.0, c?.get(2)?.toDouble() ?: 0.0, lab, i * 15 + k * 3)
            }
        }
        epoch = t
        hueSpec = Drift.spec(sceneId, "@palette")
    }

    /** Fill `out` (15 floats, sRGB) with the palette for drift time `t`. */
    fun write(out: FloatArray, t: Double) {
        if (count == 0) return
        var from = 0
        var to = 0
        var f = 0.0
        if (rotate && count > 1) {
            val cycle = maxOf(1.0, holdS + fadeS)
            val local = maxOf(0.0, t - epoch)
            val k = floor(local / cycle)
            val x = (local - k * cycle - holdS) / maxOf(1e-3, fadeS)
            f = if (x <= 0) 0.0 else if (x >= 1) 1.0 else x * x * (3 - 2 * x)
            from = (k % count).toInt()
            to = ((k + 1) % count).toInt()
        }
        current = from
        next = to
        mix = f
        val ang = if (hueDeg != 0.0) hueDeg * PI / 180 * Drift.offset(hueSpec, t) else 0.0
        val ca = cos(ang)
        val sa = sin(ang)
        for (k in 0 until 5) {
            val i = from * 15 + k * 3
            val j = to * 15 + k * 3
            val l = lab[i] + (lab[j] - lab[i]) * f
            val a = lab[i + 1] + (lab[j + 1] - lab[i + 1]) * f
            val b = lab[i + 2] + (lab[j + 2] - lab[i + 2]) * f
            oklabToSrgb(l, a * ca - b * sa, a * sa + b * ca, out, k * 3)
        }
    }

    companion object {
        private fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

        private fun toSrgb(c: Double): Double {
            val v = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
            return minOf(1.0, maxOf(0.0, v))
        }

        /** sRGB (display, 0..1) -> OKLab, written into out[o..o+2]. */
        fun srgbToOklab(r: Double, g: Double, b: Double, out: DoubleArray, o: Int) {
            val lr = toLinear(r)
            val lg = toLinear(g)
            val lb = toLinear(b)
            val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
            val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
            val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
            out[o] = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
            out[o + 1] = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
            out[o + 2] = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
        }

        /** OKLab -> sRGB (display, clamped to 0..1), written into out[o..o+2]. */
        fun oklabToSrgb(lL: Double, a: Double, b: Double, out: FloatArray, o: Int) {
            val l = cube(lL + 0.3963377774 * a + 0.2158037573 * b)
            val m = cube(lL - 0.1055613458 * a - 0.0638541728 * b)
            val s = cube(lL - 0.0894841775 * a - 1.291485548 * b)
            out[o] = toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s).toFloat()
            out[o + 1] = toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s).toFloat()
            out[o + 2] = toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s).toFloat()
        }

        private fun cube(x: Double) = x * x * x

        /** The scene's palettes for rotation: `first`, then `others` (5 colours each), deduplicated. */
        fun rotationPalettes(first: List<FloatArray>, others: List<List<FloatArray>>): List<List<FloatArray>> {
            fun key(p: List<FloatArray>) = p.joinToString(";") { c -> c.joinToString(",") { Math.round(it * 255).toString() } }
            val seen = mutableSetOf(key(first))
            val out = mutableListOf(first)
            for (p in others) if (p.size == 5 && seen.add(key(p))) out.add(p)
            return out
        }

        /** Rotation set for a scene: defaultPalette (or the fallback) first, then the presets' palettes. */
        fun scenePalettes(scene: SceneDef): List<List<FloatArray>> {
            val first = SceneFormat.palette(scene).let { p -> List(5) { k -> floatArrayOf(p[k * 3], p[k * 3 + 1], p[k * 3 + 2]) } }
            return rotationPalettes(first, scene.presets.mapNotNull { it.palette })
        }
    }
}
