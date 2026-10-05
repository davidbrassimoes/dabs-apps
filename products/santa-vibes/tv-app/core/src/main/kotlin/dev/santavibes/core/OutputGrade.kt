package dev.santavibes.core

import kotlin.math.abs

/**
 * Host-side half of shared/glsl/output.frag — port of authoring/src/grade.ts. Folds the
 * profile's contrast, saturation and temperature into a column-major mat3 + offset, and
 * black floor + brightness into levels (scale, offset). Must match
 * authoring/test/fixtures/output-grade-cases.json. Computed when the profile changes.
 */
class OutputGrade {
    val mat = FloatArray(9)
    val offset = FloatArray(3)
    val levels = FloatArray(2)
    var invGamma = 1f
        private set

    fun compute(p: Profile) {
        val tint = if (p.temperature >= 0f) WARM else COOL
        val a = abs(p.temperature)
        val k = 0.5f * (1f - p.contrast)
        for (row in 0 until 3) {
            val t = 1f + (tint[row] - 1f) * a
            for (col in 0 until 3) {
                val sat = (if (row == col) p.saturation else 0f) + (1f - p.saturation) * LUMA[col]
                mat[col * 3 + row] = t * p.contrast * sat
            }
            offset[row] = t * k
        }
        levels[0] = (1f - p.blackFloor) * p.brightness
        levels[1] = p.blackFloor * p.brightness
        invGamma = 1f / p.gamma
    }

    companion object {
        private val LUMA = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        private val WARM = floatArrayOf(1.0f, 0.86f, 0.7f)
        private val COOL = floatArrayOf(0.82f, 0.91f, 1.0f)
    }
}
