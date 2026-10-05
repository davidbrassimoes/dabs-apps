package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NoiseLatticeTest {
    @Test
    fun matchesTheSharedFixture() {
        val fx = Json.parse(File(REPO, "authoring/test/fixtures/noise-lattice.json").readText()) as Map<*, *>
        val b = NoiseLattice.generate()
        assertEquals((fx["seed"] as Double).toLong(), NoiseLattice.SEED.toLong() and 0xFFFFFFFFL)
        assertEquals((fx["size"] as Double).toInt(), NoiseLattice.SIZE)
        assertEquals((fx["first16"] as List<*>).map { (it as Double).toInt() }, (0 until 16).map { b[it].toInt() and 255 })
        assertEquals((fx["last4"] as List<*>).map { (it as Double).toInt() }, (b.size - 4 until b.size).map { b[it].toInt() and 255 })
        var h = 0x811c9dc5.toInt()
        var sum = 0L
        for (v in b) {
            h = (h xor (v.toInt() and 255)) * 0x01000193
            sum += v.toInt() and 255
        }
        assertEquals((fx["fnv1a32"] as Double).toLong(), h.toLong() and 0xFFFFFFFFL)
        assertEquals((fx["sum"] as Double).toLong(), sum)
    }
}
