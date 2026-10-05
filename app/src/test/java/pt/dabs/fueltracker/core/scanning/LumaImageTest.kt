package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Test

class LumaImageTest {

    /** 3×2: 0 1 2 / 3 4 5 */
    private val image = LumaImage(3, 2, byteArrayOf(0, 1, 2, 3, 4, 5))

    private fun LumaImage.values() = pixels.map { it.toInt() and 0xFF }

    @Test
    fun `rotations follow camera rotationDegrees (clockwise)`() {
        val r90 = image.rotate(90)
        assertEquals(2, r90.width)
        assertEquals(3, r90.height)
        assertEquals(listOf(3, 0, 4, 1, 5, 2), r90.values())
        assertEquals(listOf(5, 4, 3, 2, 1, 0), image.rotate(180).values())
        assertEquals(listOf(2, 5, 1, 4, 0, 3), image.rotate(270).values())
        assertEquals(image.values(), image.rotate(90).rotate(270).values())
        assertEquals(image.values(), image.rotate(360).values())
    }

    @Test
    fun `crop clamps to bounds`() {
        assertEquals(listOf(1, 2, 4, 5), image.crop(1, 0, 3, 2).values())
        assertEquals(listOf(4, 5), image.crop(1, 1, 10, 10).values())
    }

    @Test
    fun `center region keeps the middle`() {
        val big = LumaImage(10, 10, ByteArray(100) { it.toByte() })
        val center = big.centerRegion(0.4f, 0.2f)
        assertEquals(4, center.width)
        assertEquals(2, center.height)
        assertEquals(43, center[0, 0])
    }

    @Test
    fun `downscale averages blocks`() {
        val big = LumaImage(4, 2, byteArrayOf(0, 10, 100, 110, 20, 30, 120, (130).toByte()))
        val small = big.downscaleTo(2)
        assertEquals(2, small.width)
        assertEquals(1, small.height)
        assertEquals(listOf(15, 115), small.values())
    }

    @Test
    fun `argb uses BT601 luminance`() {
        val luma = LumaImage.fromArgb(3, 1, intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF0BE28.toInt()))
        assertEquals(listOf(255, 0, (240 * 299 + 190 * 587 + 40 * 114) / 1000), luma.values())
    }

    @Test
    fun `y plane honours row stride, pixel stride and crop`() {
        // 4×2 com rowStride 6 (2 bytes de enchimento por linha)
        val plane = byteArrayOf(0, 1, 2, 3, 99, 99, 4, 5, 6, 7, 99, 99)
        assertEquals(listOf(1, 2, 5, 6), LumaImage.fromYPlane(plane, 6, 1, 1, 0, 3, 2).values())
        val strided = byteArrayOf(0, 99, 1, 99, 2, 99, 3, 99)
        assertEquals(listOf(0, 1, 2, 3), LumaImage.fromYPlane(strided, 8, 2, 0, 0, 4, 1).values())
    }
}
