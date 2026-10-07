package dev.santavibes.core

import kotlin.math.exp

/**
 * The reactivity mixer (docs/ARQUITETURA.md, "Mixer de reatividade"): blends the real
 * source into the [Synth] signals, `out = mix(synth, real, reactivity × presence)`, and
 * fills the same three arrays the scenes read (uBands, uMood, uBeat).
 *
 * - Values from packets are smoothed over ~30 ms, which hides network jitter (protocol rule).
 * - The beat phase runs on its own at the received bpm between packets and is pulled
 *   towards each packet's phase (already predicted ahead by the sender).
 * - Presence rises over [RISE_S] when packets arrive, and falls over [FALL_S] once the
 *   source is absent: no packet for [ABSENT_S], or only silence for [SILENT_S]. A stopped
 *   PC therefore hands back to the Synth within ABSENT_S + FALL_S = 3 s, without jumps.
 * - With reactivity 0 the output is exactly the Synth.
 *
 * [onPacket] and [update] run on the GL thread and allocate nothing.
 */
class SignalMixer {
    val bands = FloatArray(4) // low, mid, high, energy
    val mood = FloatArray(4) // moodEnergy, moodBrightness, centroid, reserved
    val beat = FloatArray(4) // envelope, phase, bpm, onset

    /** 0 = ignore the real source, 1 = only the real source while it is present. */
    var reactivity = 1.0

    /** Weight of the real source in the last [update] (presence × reactivity, eased). */
    var realWeight = 0.0; private set
    /** A source is sending non-silent packets. */
    var present = false; private set
    /** The source's tempo while it is present and has one; else 0. */
    val realBpm: Float get() = if (tempoValid && present) target[SignPacket.BPM] else 0f

    private val target = FloatArray(SignPacket.FIELDS)
    private val smooth = FloatArray(SignPacket.FIELDS)
    private var hasData = false
    private var sincePacket = Double.MAX_VALUE
    private var sinceSound = Double.MAX_VALUE
    private var presence = 0.0
    private var phase = 0.0
    private var packetPhase = 0.0
    private var freshPhase = false
    private var tempoValid = false
    private var pendingOnset = false

    fun onPacket(p: SignPacket) {
        System.arraycopy(p.values, 0, target, 0, SignPacket.FIELDS)
        if (!hasData) System.arraycopy(target, 0, smooth, 0, SignPacket.FIELDS)
        hasData = true
        sincePacket = 0.0
        if (!p.silent) sinceSound = 0.0
        if (p.onset) pendingOnset = true
        val wasValid = tempoValid
        tempoValid = p.tempoValid && target[SignPacket.BPM] > 0f
        packetPhase = target[SignPacket.PHASE].toDouble()
        if (tempoValid && !wasValid) phase = packetPhase
        freshPhase = true
    }

    fun update(dt: Double, synth: Synth) {
        sincePacket += dt
        sinceSound += dt
        present = hasData && sincePacket < ABSENT_S && sinceSound < SILENT_S
        presence = if (present) minOf(1.0, presence + dt / RISE_S) else maxOf(0.0, presence - dt / FALL_S)

        val k = 1.0 - exp(-dt / SMOOTH_S)
        for (i in 0 until SignPacket.FIELDS) {
            if (i == SignPacket.PHASE || i == SignPacket.BPM) continue
            smooth[i] += ((target[i] - smooth[i]) * k).toFloat()
        }
        if (tempoValid) {
            phase += dt * target[SignPacket.BPM] / 60.0
            if (freshPhase) {
                var e = packetPhase - phase
                e -= Math.floor(e + 0.5) // wrap to [-0.5, 0.5)
                phase += PHASE_GAIN * e
            }
            phase -= Math.floor(phase)
        }
        freshPhase = false

        val r = (presence * reactivity).coerceIn(0.0, 1.0)
        val w = r * r * (3 - 2 * r) // eased: no kink when a fade starts or ends
        realWeight = w
        val wf = w.toFloat()
        bands[0] = lerp(synth.bands[0], smooth[SignPacket.LOW], wf)
        bands[1] = lerp(synth.bands[1], smooth[SignPacket.MID], wf)
        bands[2] = lerp(synth.bands[2], smooth[SignPacket.HIGH], wf)
        bands[3] = lerp(synth.bands[3], smooth[SignPacket.ENERGY], wf)
        mood[0] = lerp(synth.mood[0], smooth[SignPacket.MOOD_ENERGY], wf)
        mood[1] = lerp(synth.mood[1], smooth[SignPacket.MOOD_BRIGHTNESS], wf)
        mood[2] = lerp(synth.mood[2], smooth[SignPacket.CENTROID], wf)
        mood[3] = 0f
        beat[0] = lerp(synth.beat[0], smooth[SignPacket.BEAT], wf)
        // Phase, bpm and onset cannot be blended: the dominant source gives them.
        if (w >= 0.5) {
            beat[1] = if (tempoValid) phase.toFloat() else 0f
            beat[2] = realBpm
            beat[3] = if (pendingOnset) 1f else 0f
        } else {
            beat[1] = synth.beat[1]
            beat[2] = synth.beat[2]
            beat[3] = synth.beat[3]
        }
        pendingOnset = false
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    companion object {
        const val ABSENT_S = 1.5
        const val SILENT_S = 4.0
        const val RISE_S = 0.5
        const val FALL_S = 1.5
        const val SMOOTH_S = 0.03
        const val PHASE_GAIN = 0.3
    }
}

/**
 * Drops out-of-order packets from one sender (protocol rule), but accepts a sequence that
 * restarts: after [RESTART_S] without packets, or a jump back of more than [RESTART_JUMP].
 */
class SequenceGate {
    private var last = -1L

    fun accept(sequence: Long, sinceLastS: Double): Boolean {
        val ok = last < 0 || sequence > last || sinceLastS > RESTART_S || last - sequence > RESTART_JUMP
        if (ok) last = sequence
        return ok
    }

    companion object {
        const val RESTART_S = 2.0
        const val RESTART_JUMP = 1000L
    }
}
