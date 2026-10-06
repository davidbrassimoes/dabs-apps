package dev.santavibes.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

@Suppress("UNCHECKED_CAST")
class SignalTest {
    private val fixture = Json.parse(File(REPO, "signals-core/tests/fixtures/sign-cases.json").readText()) as Map<String, Any?>

    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun decodesTheRustEncoderOutput() {
        val p = SignPacket()
        for (c in fixture["sign"] as List<Map<String, Any?>>) {
            val b = bytes(c["hex"] as String)
            assertTrue(p.decode(b, b.size))
            assertEquals((c["kind"] as Double).toInt(), p.kind)
            assertEquals((c["flags"] as Double).toInt(), p.flags)
            assertEquals((c["sequence"] as Double).toLong(), p.sequence)
            assertEquals((c["senderTimeMs"] as Double).toLong(), p.senderTimeMs)
            (c["values"] as List<Double>).forEachIndexed { i, v -> assertEquals("value $i", v.toFloat(), p.values[i], 0f) }
        }
    }

    @Test
    fun rejectsWrongSizeMagicAndVersion() {
        val good = bytes((fixture["sign"] as List<Map<String, Any?>>)[1]["hex"] as String)
        val p = SignPacket()
        assertFalse(p.decode(good, 55))
        assertFalse(p.decode(good.copyOf().also { it[4] = 2 }, 56))
        assertFalse(p.decode(good.copyOf().also { it[0] = 'X'.code.toByte() }, 56))
        assertEquals(0, p.kind) // untouched
    }

    @Test
    fun beaconMatchesTheRustEncoder() {
        val b = fixture["beacon"] as Map<String, Any?>
        val out = ByteArray(16)
        SignPacket.encodeBeacon(out, 47800, 0, (b["deviceId"] as Double).toLong().toInt())
        assertArrayEquals(bytes(b["hex"] as String), out)
    }

    private fun packet(energy: Float, bpm: Float = 0f, phase: Float = 0f, onset: Boolean = false, silent: Boolean = false) =
        SignPacket().apply {
            kind = SignPacket.SOURCE_PC
            for (i in 0 until SignPacket.FIELDS) values[i] = energy
            values[SignPacket.BPM] = bpm
            values[SignPacket.PHASE] = phase
            flags = (if (onset) SignPacket.FLAG_ONSET else 0) or (if (bpm > 0) SignPacket.FLAG_TEMPO_VALID else 0) or
                (if (silent) SignPacket.FLAG_SILENCE else 0)
        }

    @Test
    fun reactivityZeroIsExactlyTheSynth() {
        val synth = Synth()
        val m = SignalMixer().apply { reactivity = 0.0 }
        for (f in 0 until 300) {
            m.onPacket(packet(0.9f, 128f, (f % 30) / 30f, onset = f % 15 == 0))
            synth.update(1 / 30.0, f / 30.0)
            m.update(1 / 30.0, synth)
            assertArrayEquals(synth.bands, m.bands, 0f)
            assertArrayEquals(synth.beat, m.beat, 0f)
            assertArrayEquals(synth.mood, m.mood, 0f)
        }
    }

    @Test
    fun aStoppedSourceHandsBackWithin3sWithoutJumps() {
        val synth = Synth().apply { beatEnabled = false } // smooth, so any jump would be the mixer
        val m = SignalMixer()
        val dt = 1 / 30.0
        var t = 0.0
        var prev = Float.NaN
        var maxStep = 0f
        var backAt = -1.0
        for (f in 0 until 30 * 12) {
            if (t < 5.0) m.onPacket(packet(1f))
            synth.update(dt, t)
            m.update(dt, synth)
            if (t > 4.0) {
                if (!prev.isNaN()) maxStep = maxOf(maxStep, abs(m.bands[3] - prev))
                prev = m.bands[3]
            }
            if (t in 4.5..4.6) assertEquals(1.0, m.realWeight, 1e-9)
            if (t > 5.0 && backAt < 0 && m.realWeight == 0.0) backAt = t - 5.0
            t += dt
        }
        assertTrue("back to synth after ${backAt}s", backAt in 0.0..3.0)
        assertTrue("largest per-frame step $maxStep", maxStep < 0.05f)
    }

    @Test
    fun longSilenceAlsoHandsBack() {
        val synth = Synth()
        val m = SignalMixer()
        for (f in 0 until 30 * 3) { m.onPacket(packet(0.8f)); synth.update(1 / 30.0, 0.0); m.update(1 / 30.0, synth) }
        assertTrue(m.present)
        for (f in 0 until 30 * 7) { m.onPacket(packet(0f, silent = true)); synth.update(1 / 30.0, 0.0); m.update(1 / 30.0, synth) }
        assertFalse(m.present)
        assertEquals(0.0, m.realWeight, 0.0)
    }

    @Test
    fun phaseRunsBetweenPacketsAndOnsetsReachTheScene() {
        val synth = Synth()
        val m = SignalMixer()
        // Packets at 10 Hz, frames at 30 Hz, 120 BPM: phase must keep moving between packets.
        var onsets = 0
        var t = 0.0
        var prevPhase = -1f
        var forward = 0
        for (f in 0 until 30 * 6) {
            if (f % 3 == 0) {
                val ph = ((t * 2.0) % 1.0).toFloat()
                m.onPacket(packet(0.7f, 120f, ph, onset = ph < 0.2f && ((t * 2.0) % 1.0) * 10 < 2 && f % 15 == 0))
            }
            synth.update(1 / 30.0, t)
            m.update(1 / 30.0, synth)
            if (t > 1.0) {
                assertEquals(120f, m.beat[2], 0f)
                if (prevPhase >= 0 && (m.beat[1] > prevPhase || prevPhase - m.beat[1] > 0.5f)) forward++
                onsets += m.beat[3].toInt()
            }
            prevPhase = m.beat[1]
            t += 1 / 30.0
        }
        assertTrue("phase moved forward on $forward frames", forward > 140)
        assertTrue("onsets $onsets", onsets > 0)
    }

    @Test
    fun sequenceGateDropsOldPacketsButAcceptsARestart() {
        val g = SequenceGate()
        assertTrue(g.accept(10, 0.0))
        assertTrue(g.accept(11, 0.01))
        assertFalse(g.accept(11, 0.01))
        assertFalse(g.accept(9, 0.01))
        assertTrue(g.accept(0, 3.0)) // sender restarted after a pause
        assertTrue(g.accept(5000, 0.01))
        assertTrue(g.accept(3, 0.01)) // restarted at once: big jump back
    }
}
