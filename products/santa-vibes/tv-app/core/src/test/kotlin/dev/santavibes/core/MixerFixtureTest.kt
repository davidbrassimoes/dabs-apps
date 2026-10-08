package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.sin

/**
 * authoring/test/fixtures/mixer-cases.json: the [SignalMixer] reference run that the authoring's
 * port (authoring/src/mixer.ts) must reproduce, so scenes react the same in the browser and on
 * the TV (ROADMAP 1.6). Regenerate after changing the mixer:
 *   SV_WRITE_FIXTURES=1 ./gradlew :core:test --tests '*MixerFixtureTest*'
 */
class MixerFixtureTest {
    private val file = File(REPO, "authoring/test/fixtures/mixer-cases.json")

    @Test
    fun matchesTheSharedFixture() {
        val text = generate()
        if (System.getenv("SV_WRITE_FIXTURES") == "1") file.writeText(text)
        assertEquals("regenerate with SV_WRITE_FIXTURES=1", file.readText().replace("\r\n", "\n"), text)
    }

    /** 8 s at 30 fps: packets from 0.5 s to 5 s (some frames without one), then the source stops. */
    private fun generate(): String {
        val dt = 1 / 30.0
        val synth = Synth()
        val m = SignalMixer().apply { reactivity = 0.7 }
        val p = SignPacket()
        val sb = StringBuilder("{\n \"dt\": $dt,\n \"reactivity\": 0.7,\n \"frames\": [\n")
        var t = 0.0
        for (f in 0 until 240) {
            val send = t in 0.5..5.0 && f % 4 != 3
            if (send) {
                val ph = ((t * 2.0 + 0.1) % 1.0).toFloat()
                p.kind = SignPacket.SOURCE_PC
                p.values[SignPacket.ENERGY] = (0.5 + 0.4 * sin(t * 3.0)).toFloat()
                p.values[SignPacket.LOW] = (0.6 + 0.3 * sin(t * 5.0)).toFloat()
                p.values[SignPacket.MID] = (0.4 + 0.2 * sin(t * 2.0)).toFloat()
                p.values[SignPacket.HIGH] = (0.3 + 0.1 * sin(t * 7.0)).toFloat()
                p.values[SignPacket.CENTROID] = 0.55f
                p.values[SignPacket.BEAT] = if (ph < 0.07f) 1f else (1f - ph)
                p.values[SignPacket.PHASE] = ph
                p.values[SignPacket.BPM] = 120f
                p.values[SignPacket.MOOD_ENERGY] = 0.7f
                p.values[SignPacket.MOOD_BRIGHTNESS] = 0.35f
                p.flags = SignPacket.FLAG_TEMPO_VALID or (if (ph < 0.07f) SignPacket.FLAG_ONSET else 0)
                m.onPacket(p)
            }
            synth.update(dt, t)
            val synthIn = synth.bands.toList() + synth.mood.toList() + synth.beat.toList()
            m.update(dt, synth)
            val out = m.bands.toList() + m.mood.toList() + m.beat.toList()
            sb.append("  {\"synth\": ").append(synthIn.joinToString(",", "[", "]"))
            if (send) sb.append(", \"packet\": {\"values\": ").append(p.values.joinToString(",", "[", "]"))
                .append(", \"flags\": ").append(p.flags).append('}')
            sb.append(", \"out\": ").append(out.joinToString(",", "[", "]")).append('}')
            sb.append(if (f < 239) ",\n" else "\n")
            t += dt
        }
        return sb.append(" ]\n}\n").toString()
    }
}
