package dev.santavibes.core

/**
 * The 5-colour palette of an image (docs/FONTES-IMAGEM.md, "Extração de paleta"): k-means in
 * OKLab over a small thumbnail (~64×64), deterministic (no randomness: the TV and the authoring
 * must agree, checked by authoring/test/fixtures/palette-extract-cases.json), sorted from dark to
 * light like the scenes' own palettes.
 *
 * - Start: the pixels at the 10/30/50/70/90 % lightness quantiles.
 * - [ITERATIONS] Lloyd steps; a cluster that empties keeps its centre.
 *
 * Runs once per image on a background thread; allocates its working arrays.
 */
object PaletteExtract {
    const val K = 5
    const val ITERATIONS = 12

    /** `argb`: packed 0xAARRGGBB pixels (alpha ignored). Returns 15 floats, sRGB 0..1, dark to light. */
    fun extract(argb: IntArray): FloatArray {
        val n = argb.size
        require(n >= K) { "need at least $K pixels" }
        val lab = DoubleArray(n * 3)
        for (i in 0 until n) {
            val p = argb[i]
            PaletteVariation.srgbToOklab(((p shr 16) and 0xff) / 255.0, ((p shr 8) and 0xff) / 255.0, (p and 0xff) / 255.0, lab, i * 3)
        }
        val order = (0 until n).sortedWith(compareBy({ lab[it * 3] }, { it }))
        val c = DoubleArray(K * 3)
        for (k in 0 until K) {
            val idx = order[((0.1 + 0.2 * k) * (n - 1)).toInt()]
            for (d in 0..2) c[k * 3 + d] = lab[idx * 3 + d]
        }
        val sum = DoubleArray(K * 3)
        val count = IntArray(K)
        repeat(ITERATIONS) {
            sum.fill(0.0)
            count.fill(0)
            for (i in 0 until n) {
                var best = 0
                var bestD = Double.MAX_VALUE
                for (k in 0 until K) {
                    val dl = lab[i * 3] - c[k * 3]
                    val da = lab[i * 3 + 1] - c[k * 3 + 1]
                    val db = lab[i * 3 + 2] - c[k * 3 + 2]
                    val dd = dl * dl + da * da + db * db
                    if (dd < bestD) {
                        bestD = dd
                        best = k
                    }
                }
                count[best]++
                for (d in 0..2) sum[best * 3 + d] += lab[i * 3 + d]
            }
            for (k in 0 until K) if (count[k] > 0) for (d in 0..2) c[k * 3 + d] = sum[k * 3 + d] / count[k]
        }
        val byL = (0 until K).sortedWith(compareBy({ c[it * 3] }, { it }))
        val out = FloatArray(K * 3)
        for ((j, k) in byL.withIndex()) PaletteVariation.oklabToSrgb(c[k * 3], c[k * 3 + 1], c[k * 3 + 2], out, j * 3)
        return out
    }
}
