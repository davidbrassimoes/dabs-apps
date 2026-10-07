package dev.santavibes.core

import kotlin.math.PI
import kotlin.math.sin

/**
 * Parameter drift — port of authoring/src/drift.ts (docs/ARQUITETURA.md, "Drift de
 * parâmetros"). Must match authoring/test/fixtures/drift-cases.json within 1e-9.
 *
 * A [Spec] is computed once per scene and param; [offset] and [value] allocate nothing.
 */
object Drift {
    class Spec(val f1: Double, val f2: Double, val ph1: Double, val ph2: Double)

    private const val TAU = PI * 2

    /** FNV-1a, 32 bits, over the UTF-16 code units of `s`; returned as an unsigned value. */
    fun fnv1a(s: String): Long {
        var h = 0x811c9dc5.toInt()
        for (ch in s) {
            h = h xor ch.code
            h *= 0x01000193
        }
        return h.toLong() and 0xffffffffL
    }

    /** xorshift32 step on the low 32 bits. */
    fun xorshift32(x: Int): Int {
        var v = x
        v = v xor (v shl 13)
        v = v xor (v ushr 17)
        v = v xor (v shl 5)
        return v
    }

    fun spec(sceneId: String, paramId: String): Spec {
        var x = fnv1a("$sceneId/$paramId").toInt().let { if (it == 0) 1 else it }
        fun next(): Double {
            x = xorshift32(x)
            return (x.toLong() and 0xffffffffL) / 4294967296.0
        }
        val u1 = next()
        val u2 = next()
        val u3 = next()
        val f1 = 1.0 / (30.0 + 90.0 * u1)
        return Spec(f1, f1 * 2.618, TAU * u2, TAU * u3)
    }

    /** Smooth offset in [-1, 1] at drift time t (seconds). */
    fun offset(s: Spec, t: Double): Double =
        0.65 * sin(TAU * s.f1 * t + s.ph1) + 0.35 * sin(TAU * s.f2 * t + s.ph2)

    /** Effective value of a float/int param; `amount` is the global drift multiplier (1 = as authored). */
    fun value(p: ParamDef, base: Double, s: Spec, t: Double, amount: Double): Double {
        if (p.drift <= 0 || amount <= 0 || (p.type != ParamType.FLOAT && p.type != ParamType.INT)) return base
        val v = minOf(p.max, maxOf(p.min, base + offset(s, t) * 0.5 * p.drift * amount * (p.max - p.min)))
        // Math.round: half up, like JS Math.round.
        return if (p.type == ParamType.INT) Math.round(v).toDouble() else v
    }
}
