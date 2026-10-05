package dev.santavibes.core

/**
 * The uNoise texture (docs/FORMATO-CENA.md, v3): 256x256 single-channel bytes from
 * xorshift32 seeded with 0x9E3779B9; byte i (row-major, row 0 uploaded first) is the
 * top 8 bits of the state after step i+1. Every host must produce the same bytes —
 * authoring/test/fixtures/noise-lattice.json is the shared check.
 */
object NoiseLattice {
    const val SIZE = 256
    const val SEED = 0x9E3779B9.toInt()

    fun generate(): ByteArray {
        val out = ByteArray(SIZE * SIZE)
        var x = SEED
        for (i in out.indices) {
            x = x xor (x shl 13)
            x = x xor (x ushr 17)
            x = x xor (x shl 5)
            out[i] = (x ushr 24).toByte()
        }
        return out
    }
}
