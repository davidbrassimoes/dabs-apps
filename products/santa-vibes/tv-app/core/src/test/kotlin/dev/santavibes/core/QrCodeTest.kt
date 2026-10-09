package dev.santavibes.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QrCodeTest {
    private val cases = (Json.parse(File(REPO, "tv-app/core/src/test/fixtures/qr-cases.json").readText()) as Map<*, *>)["cases"] as List<*>

    private fun rows(qr: QrCode) = (0 until qr.size).map { y -> (0 until qr.size).joinToString("") { x -> if (qr.get(x, y)) "1" else "0" } }

    @Test
    fun matchesReferenceForEveryMask() {
        for (c in cases) {
            c as Map<*, *>
            val mask = (c["mask"] as Double).toInt()
            if (mask < 0) continue
            val qr = QrCode.encode(c["text"] as String, forceMask = mask)
            assertEquals("version of ${c["text"]}", (c["version"] as Double).toInt(), qr.version)
            assertEquals("matrix of ${c["text"]} with mask $mask", c["rows"], rows(qr))
        }
    }

    @Test
    fun chosenMaskMatchesReference() {
        for (c in cases) {
            c as Map<*, *>
            if ((c["mask"] as Double).toInt() >= 0) continue
            val qr = QrCode.encode(c["text"] as String)
            assertEquals("mask chosen for ${c["text"]}", (c["chosenMask"] as Double).toInt(), qr.mask)
            assertEquals(c["rows"], rows(qr))
        }
    }

    @Test
    fun rejectsTooLong() {
        assertThrows(IllegalArgumentException::class.java) { QrCode.encode("x".repeat(107)) }
    }
}
