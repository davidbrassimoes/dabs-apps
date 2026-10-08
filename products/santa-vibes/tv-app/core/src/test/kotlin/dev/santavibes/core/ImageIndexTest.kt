package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageIndexTest {
    private fun entry(key: String, bytes: Long, used: Long, source: String = "aic") =
        ImageIndex.Entry(key, source, "$key.jpg", bytes, "Título \"$key\"", "Autor", "Museu", "CC0", FloatArray(15) { it / 15f }, used)

    @Test
    fun evictsTheLeastRecentlyUsedPastTheLimit() {
        val idx = ImageIndex(maxBytes = 250)
        idx.add(entry("a", 100, 1))
        idx.add(entry("b", 100, 3))
        val evicted = idx.add(entry("c", 100, 2))
        assertEquals(listOf("a"), evicted.map { it.key })
        assertEquals(setOf("b", "c"), idx.entries.map { it.key }.toSet())
    }

    @Test
    fun neverEvictsTheOneJustAdded() {
        val idx = ImageIndex(maxBytes = 50)
        idx.add(entry("old", 40, 99))
        idx.add(entry("new", 40, 1))
        assertEquals(listOf("new"), idx.entries.map { it.key })
    }

    @Test
    fun nextRotatesThroughTheLeastRecentlyShownOfTheChosenSources() {
        val idx = ImageIndex()
        idx.add(entry("a", 1, 5))
        idx.add(entry("b", 1, 2))
        idx.add(entry("s", 1, 0, source = "server"))
        assertEquals("b", idx.next(setOf("aic"), null, 10)?.key)
        assertEquals("a", idx.next(setOf("aic"), "b", 11)?.key)
        assertEquals("s", idx.next(null, "a", 12)?.key)
        assertNull(idx.next(setOf("met"), null, 13))
        // Only one image: it stays.
        val one = ImageIndex().apply { add(entry("x", 1, 0)) }
        assertEquals("x", one.next(null, "x", 1)?.key)
    }

    @Test
    fun roundTripsThroughJson() {
        val idx = ImageIndex(1000).apply {
            add(entry("a", 10, 7))
            add(entry("b\\n", 20, 8))
        }
        val back = ImageIndex.fromJson(idx.toJson(), 1000)
        assertEquals(idx.entries.map { it.key }, back.entries.map { it.key })
        val e = back.entries[0]
        assertEquals("Título \"a\"", e.title)
        assertEquals(7L, e.lastUsed)
        assertEquals(10L, e.bytes)
        assertEquals(14 / 15f, e.palette[14], 1e-6f)
        assertEquals("Título \"a\" · Autor · Museu", e.credit())
    }

    @Test
    fun aBrokenIndexIsEmptyNotACrash() {
        assertTrue(ImageIndex.fromJson("{\"entries\":[{\"key\":\"x\"}]}", 10).entries.isEmpty())
    }

    @Test
    fun readsApacheAndPythonListings() {
        val apache = """<tr><td><a href="?C=N;O=D">Name</a></td></tr>
            <tr><td><a href="/">Parent Directory</a></td></tr>
            <tr><td><a href="Praia%20da%20Luz.jpg">Praia da Luz.jpg</a></td></tr>
            <tr><td><a href="notes.txt">notes.txt</a></td></tr>
            <tr><td><a href="sub/">sub/</a></td></tr>
            <tr><td><a href="ceu.PNG">ceu.PNG</a></td></tr>"""
        val c = ImageSources.parseListing(apache, "http://192.168.1.200/santa-vibes/")
        assertEquals(listOf("Praia da Luz.jpg", "ceu.PNG"), c.map { it.id })
        assertEquals("http://192.168.1.200/santa-vibes/Praia%20da%20Luz.jpg", c[0].url)
        assertEquals("Praia da Luz", c[0].title)
        assertEquals("server:ceu.PNG", c[1].key)
        val python = """<li><a href="a%C3%A7ores.jpeg">açores.jpeg</a></li><li><a href="../x.jpg">x</a></li>"""
        assertEquals(listOf("açores.jpeg"), ImageSources.parseListing(python, "http://h:8099/").map { it.id })
    }

    @Test
    fun selectionParsing() {
        assertEquals(ImageSources.ALL.toSet(), ImageSources.parseSelection(null))
        assertEquals(ImageSources.ALL.toSet(), ImageSources.parseSelection("all"))
        assertNull(ImageSources.parseSelection("off"))
        assertEquals(setOf("met", "server"), ImageSources.parseSelection("met, server, aic"))
        assertNull(ImageSources.parseSelection("bogus"))
    }
}
