package dev.santavibes.core

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Synthetic signals: slow LFOs plus a metronome beat — what scenes see with no source
 * (reactivity 0). Port of the "auto" mode of authoring/src/signals.ts; the arrays are
 * uploaded as-is to uBands / uMood / uBeat. update() allocates nothing.
 */
class Synth {
    val bands = FloatArray(4) // low, mid, high, energy
    val mood = FloatArray(4) // moodEnergy, moodBrightness, centroid, reserved
    val beat = FloatArray(4) // envelope, phase, bpm, onset

    var bpm = 100.0
    var beatEnabled = true
    /** 0..1, how much the beat envelope leaks into the fast bands. */
    var punch = 0.5

    private var env = 0.0
    private var phase = 0.0

    fun update(dt: Double, t: Double) {
        var onset = 0.0
        if (beatEnabled && bpm > 0) {
            val next = phase + dt * bpm / 60.0
            if (next >= 1) onset = 1.0
            phase = next - floor(next)
        } else {
            phase = 0.0
        }
        env = if (onset > 0) 1.0 else env * exp(-dt * 5.5)

        var energy = 0.35 + 0.22 * sin(t * 0.13) + 0.08 * sin(t * 0.71 + 1.0)
        val low = energy + 0.12 * sin(t * 0.9 + 1.0) + punch * 0.45 * env
        val mid = energy * 0.85 + 0.1 * sin(t * 1.3 + 2.0) + punch * 0.15 * env
        val high = energy * 0.6 + 0.08 * sin(t * 2.1 + 0.5) + punch * 0.1 * env
        energy += punch * 0.25 * env

        bands[0] = clamp01(low)
        bands[1] = clamp01(mid)
        bands[2] = clamp01(high)
        bands[3] = clamp01(energy)
        mood[0] = clamp01(0.45 + 0.25 * sin(t * 0.021))
        mood[1] = clamp01(0.5 + 0.2 * sin(t * 0.017 + 2.0))
        mood[2] = clamp01(0.5 + 0.15 * sin(t * 0.05 + 1.0))
        mood[3] = 0f
        beat[0] = env.toFloat()
        beat[1] = phase.toFloat()
        beat[2] = if (beatEnabled) bpm.toFloat() else 0f
        beat[3] = onset.toFloat()
    }

    private fun clamp01(v: Double): Float = (if (v < 0) 0.0 else if (v > 1) 1.0 else v).toFloat()
}
