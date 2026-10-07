package dev.santavibes.phone

/** signals-core through JNI (phone-app/native). The handle is an opaque pointer. */
object SignalsNative {
    init {
        System.loadLibrary("santavibes_signals")
    }

    external fun create(sampleRate: Int, lookaheadMs: Float): Long

    external fun destroy(handle: Long)

    /** Interleaved PCM, `count` floats (all channels). */
    external fun push(handle: Long, samples: FloatArray, count: Int, channels: Int)

    /** Writes the latest signals as a 56-byte SIGN packet into `out`; returns its flags. */
    external fun packet(handle: Long, kind: Int, sequence: Int, timeMs: Int, out: ByteArray): Int

    const val KIND_MIC = 2
    const val KIND_PLAYBACK = 3
    const val FLAG_SILENCE = 8
    const val FLAG_TEMPO_VALID = 2
}
