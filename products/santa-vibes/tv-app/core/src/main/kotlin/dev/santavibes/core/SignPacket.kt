package dev.santavibes.core

/**
 * `SIGN` and `PNMB` packets, docs/PROTOCOLO-SIGNALS.md v1 (little-endian). Mirrors
 * signals-core/src/packet.rs; both are checked against signals-core/tests/fixtures/sign-cases.json.
 * [decode] fills this instance in place: no allocation per packet.
 */
class SignPacket {
    var kind = 0
    var flags = 0
    /** Unsigned 32-bit values, kept in a Long. */
    var sequence = 0L
    var senderTimeMs = 0L
    /** energy, low, mid, high, centroid, beat, beatPhase, bpm, moodEnergy, moodBrightness. */
    val values = FloatArray(FIELDS)

    val onset: Boolean get() = flags and FLAG_ONSET != 0
    val tempoValid: Boolean get() = flags and FLAG_TEMPO_VALID != 0
    val silent: Boolean get() = flags and FLAG_SILENCE != 0

    fun copyFrom(o: SignPacket) {
        kind = o.kind
        flags = o.flags
        sequence = o.sequence
        senderTimeMs = o.senderTimeMs
        System.arraycopy(o.values, 0, values, 0, FIELDS)
    }

    /** Decode `len` bytes of `b`; false (and this instance untouched) for a wrong size, magic or version. */
    fun decode(b: ByteArray, len: Int): Boolean {
        if (len != SIGN_LEN || b[0] != 'S'.code.toByte() || b[1] != 'I'.code.toByte() ||
            b[2] != 'G'.code.toByte() || b[3] != 'N'.code.toByte() || b[4].toInt() != VERSION
        ) return false
        kind = b[5].toInt() and 0xff
        flags = u16(b, 6)
        sequence = u32(b, 8)
        senderTimeMs = u32(b, 12)
        for (i in 0 until FIELDS) values[i] = java.lang.Float.intBitsToFloat(u32(b, 16 + i * 4).toInt())
        return true
    }

    companion object {
        const val VERSION = 1
        const val SIGN_LEN = 56
        const val BEACON_LEN = 16
        const val SIGNAL_PORT = 47800
        const val BEACON_PORT = 47801
        const val FIELDS = 10
        const val FLAG_ONSET = 1
        const val FLAG_TEMPO_VALID = 2
        const val FLAG_MIDI_LOCKED = 4
        const val FLAG_SILENCE = 8

        // Indices into [values].
        const val ENERGY = 0
        const val LOW = 1
        const val MID = 2
        const val HIGH = 3
        const val CENTROID = 4
        const val BEAT = 5
        const val PHASE = 6
        const val BPM = 7
        const val MOOD_ENERGY = 8
        const val MOOD_BRIGHTNESS = 9

        // sourceKind
        const val SOURCE_PC = 1
        const val SOURCE_PHONE_MIC = 2
        const val SOURCE_PHONE_PLAYBACK = 3
        const val SOURCE_MIDI = 4

        private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)

        private fun u32(b: ByteArray, o: Int): Long =
            (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)

        /** The TV's beacon: 16 bytes written into `out` (allocated once by the caller). */
        fun encodeBeacon(out: ByteArray, signalPort: Int, controlPort: Int, deviceId: Int) {
            java.util.Arrays.fill(out, 0, BEACON_LEN, 0)
            out[0] = 'P'.code.toByte(); out[1] = 'N'.code.toByte(); out[2] = 'M'.code.toByte(); out[3] = 'B'.code.toByte()
            out[4] = VERSION.toByte()
            out[6] = signalPort.toByte(); out[7] = (signalPort shr 8).toByte()
            out[8] = controlPort.toByte(); out[9] = (controlPort shr 8).toByte()
            for (i in 0 until 4) out[12 + i] = (deviceId shr (8 * i)).toByte()
        }
    }
}
