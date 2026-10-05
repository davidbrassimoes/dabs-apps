package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@Suppress("UNCHECKED_CAST")
class OutputGradeTest {
    @Test
    fun matchesTheSharedFixtures() {
        val cases = Json.parse(File(REPO, "authoring/test/fixtures/output-grade-cases.json").readText()) as List<Map<String, Any?>>
        for (c in cases) {
            val p = c["profile"] as Map<String, Any?>
            fun f(k: String) = (p[k] as Double).toFloat()
            val profile = Profile("", "", f("brightness"), f("contrast"), f("saturation"), f("gamma"), f("blackFloor"), f("temperature"), 1f, 0.5, 30)
            val g = OutputGrade().apply { compute(profile) }
            val name = c["name"] as String
            (c["mat"] as List<Double>).forEachIndexed { i, v -> assertEquals("$name mat[$i]", v.toFloat(), g.mat[i], 1e-5f) }
            (c["offset"] as List<Double>).forEachIndexed { i, v -> assertEquals("$name offset[$i]", v.toFloat(), g.offset[i], 1e-5f) }
            (c["levels"] as List<Double>).forEachIndexed { i, v -> assertEquals("$name levels[$i]", v.toFloat(), g.levels[i], 1e-5f) }
            assertEquals(name, (c["invGamma"] as Double).toFloat(), g.invGamma, 1e-6f)
        }
    }
}
