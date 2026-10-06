package dev.santavibes.phone

/** What the screen shows: written by the sender thread, read by the UI twice a second. */
object Status {
    @Volatile var active = false
    @Volatile var kind = 0
    @Volatile var sampleRate = 0
    @Volatile var channels = 0
    @Volatile var error: String? = null
    @Volatile var sent = 0L
    @Volatile var tvCount = 0
    @Volatile var energy = 0f
    @Volatile var low = 0f
    @Volatile var bpm = 0f
    @Volatile var tempoValid = false
    @Volatile var silent = false
    @Volatile var tvs: () -> String = { "" }

    fun reset(kind: Int, sampleRate: Int, channels: Int) {
        this.kind = kind
        this.sampleRate = sampleRate
        this.channels = channels
        error = null
        sent = 0
        active = true
    }

    /** From the packet bytes (layout in docs/PROTOCOLO-SIGNALS.md). No allocation. */
    fun update(p: ByteArray, flags: Int, sentNow: Int, tvs: Int) {
        energy = f(p, 16)
        low = f(p, 20)
        bpm = f(p, 44)
        tempoValid = flags and SignalsNative.FLAG_TEMPO_VALID != 0
        silent = flags and SignalsNative.FLAG_SILENCE != 0
        sent += sentNow
        tvCount = tvs
    }

    private fun f(p: ByteArray, o: Int): Float = java.lang.Float.intBitsToFloat(
        (p[o].toInt() and 0xff) or ((p[o + 1].toInt() and 0xff) shl 8) or ((p[o + 2].toInt() and 0xff) shl 16) or (p[o + 3].toInt() shl 24),
    )
}
