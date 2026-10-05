package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Repository root, passed by Gradle (core/build.gradle.kts). */
internal val REPO = File(System.getProperty("santavibes.repo") ?: "..")

@Suppress("UNCHECKED_CAST")
class ComposeTest {
    private val cases = Json.parse(File(REPO, "authoring/test/fixtures/compose-cases.json").readText()) as List<Map<String, Any?>>

    private fun compose(c: Map<String, Any?>): Composed = Compose.compose(
        c["header"] as String,
        (c["params"] as List<Map<String, Any?>>).map(SceneFormat::param),
        c["body"] as String,
        c["bodyFile"] as String,
        c["includes"] as Map<String, String?>,
    )

    @Test
    fun fixturesProduceTheReferenceTextAndOrigins() {
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val r = compose(c)
            val expected = c["expected"] as Map<String, Any?>
            assertEquals(c["name"] as String, expected["source"], r.source)
            val origins = (expected["origins"] as List<List<Any?>>).map { "${it[0]}:${(it[1] as Double).toInt()}" }
            assertEquals(c["name"] as String, origins, r.origins.map { "${it.file}:${it.line}" })
            assertEquals(r.source.split("\n").size - 1, r.origins.size)
        }
    }

    @Test
    fun errorLogMapsToAuthorFiles() {
        val c = cases.first { (it["name"] as String).startsWith("nested includes") }
        val r = compose(c)
        val mapped = Compose.mapErrorLog(
            "ERROR: 0:6: 'fa' : no matching overloaded function found\nWARNING: 0:8: something\nunparsed line\n0:6: L0001: powervr style",
            r,
        )
        assertEquals(listOf("shared/glsl/lib/b.glsl:2", "scenes/d/scene.frag:3", ":0", "shared/glsl/lib/b.glsl:2"), mapped.map { "${it.file}:${it.line}" })
        assertEquals("warning: something", mapped[1].message)
        assertEquals("L0001: powervr style", mapped[3].message)
    }

    @Test
    fun everyRealSceneParsesAndComposesWithoutMissingIncludes() {
        val glsl = File(REPO, "shared/glsl")
        val header = File(glsl, "header.glsl").readText()
        val dirs = File(REPO, "scenes").listFiles()!!.filter { File(it, "scene.json").exists() }
        assertTrue(dirs.size >= 15)
        for (dir in dirs) {
            val scene = SceneFormat.parseScene(File(dir, "scene.json").readText(), dir.name)
            val body = File(dir, "scene.frag").readText()
            val includes = Compose.collectIncludes(body, { p -> File(glsl, p).takeIf { it.exists() }?.readText() })
            val r = Compose.compose(header, scene.params, body, "scenes/${dir.name}/scene.frag", includes)
            assertFalse(dir.name, r.source.contains("#error include not found"))
            assertTrue(dir.name, r.source.startsWith("#version 300 es"))
        }
    }
}
