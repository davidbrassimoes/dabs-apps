package pt.dabs.fueltracker.core.scanning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OdometerReaderTest {

    @Test
    fun `plain digits`() {
        assertEquals(listOf(123_456), OdometerReader.extractCandidates(listOf("123456")))
        assertEquals(listOf(123_456), OdometerReader.extractCandidates(listOf("123456 km")))
        assertEquals(listOf(123_456), OdometerReader.extractCandidates(listOf("123456km")))
    }

    @Test
    fun `thousands grouping with space or dot is joined`() {
        assertEquals(listOf(123_456), OdometerReader.extractCandidates(listOf("123 456")))
        assertEquals(listOf(123_456), OdometerReader.extractCandidates(listOf("123.456")))
        assertEquals(listOf(1_234_567), OdometerReader.extractCandidates(listOf("1 234 567")))
    }

    @Test
    fun `trip meter with decimals is ignored`() {
        assertEquals(listOf(123_456), OdometerReader.extractCandidates(listOf("123456", "412,3")))
        assertEquals(listOf(123_456), OdometerReader.extractCandidates(listOf("123456 412.3")))
    }

    @Test
    fun `seven segment confusions are repaired`() {
        assertEquals(listOf(120_456), OdometerReader.extractCandidates(listOf("I2O456")))
        assertEquals(listOf(105_280), OdometerReader.extractCandidates(listOf("1O528O")))
        assertEquals(listOf(258_610), OdometerReader.extractCandidates(listOf("2S86lO")))
    }

    @Test
    fun `words and short numbers are not candidates`() {
        assertEquals(emptyList<Int>(), OdometerReader.extractCandidates(listOf("ODO", "TRIP", "km/h", "12", "123")))
        assertEquals(emptyList<Int>(), OdometerReader.extractCandidates(listOf("12345678")))
        assertEquals(emptyList<Int>(), OdometerReader.extractCandidates(listOf("DIESEL")))
    }

    @Test
    fun `several candidates keep their order`() {
        assertEquals(listOf(123_456, 4_123), OdometerReader.extractCandidates(listOf("123456", "4123")))
    }

    @Test
    fun `pick requires progress from the last odometer within the maximum distance`() {
        assertEquals(123_456, OdometerReader.pickPlausible(listOf(123_456), lastOdometerKm = 122_900))
        assertNull(OdometerReader.pickPlausible(listOf(122_900), lastOdometerKm = 122_900))
        assertNull(OdometerReader.pickPlausible(listOf(122_000), lastOdometerKm = 122_900))
        assertNull(OdometerReader.pickPlausible(listOf(126_000), lastOdometerKm = 122_900))
        assertEquals(125_900, OdometerReader.pickPlausible(listOf(125_900), lastOdometerKm = 122_900))
    }

    @Test
    fun `pick prefers the total over the trip`() {
        assertEquals(123_456, OdometerReader.pickPlausible(listOf(4_123, 123_456), lastOdometerKm = null))
        // com odómetro anterior, o parcial nem sequer é plausível
        assertEquals(123_456, OdometerReader.pickPlausible(listOf(4_123, 123_456), lastOdometerKm = 123_000))
    }

    @Test
    fun `without last odometer, short values are rejected`() {
        assertNull(OdometerReader.pickPlausible(listOf(999), lastOdometerKm = null))
        assertEquals(1_000, OdometerReader.pickPlausible(listOf(1_000), lastOdometerKm = null))
    }

    @Test
    fun `read combines extraction and choice`() {
        assertEquals(123_456, OdometerReader.read(listOf("ODO 123 456 km", "TRIP 412,3"), lastOdometerKm = 123_000))
        assertNull(OdometerReader.read(listOf("TRIP 412,3"), lastOdometerKm = 123_000))
    }

    @Test
    fun `stabilizer locks after n equal frames`() {
        val stabilizer = OdometerStabilizer(requiredFrames = 3)
        assertNull(stabilizer.offer(123_456))
        assertNull(stabilizer.offer(123_456))
        assertEquals(123_456, stabilizer.offer(123_456))
        assertEquals(123_456, stabilizer.candidate)
    }

    @Test
    fun `a different value restarts the count`() {
        val stabilizer = OdometerStabilizer(requiredFrames = 3)
        stabilizer.offer(123_456)
        stabilizer.offer(123_456)
        assertNull(stabilizer.offer(123_458))
        assertNull(stabilizer.offer(123_458))
        assertEquals(123_458, stabilizer.offer(123_458))
    }

    @Test
    fun `empty frames are tolerated up to the gap limit`() {
        val stabilizer = OdometerStabilizer(requiredFrames = 3, maxGap = 2)
        stabilizer.offer(123_456)
        stabilizer.offer(null)
        stabilizer.offer(null)
        stabilizer.offer(123_456)
        assertEquals(123_456, stabilizer.offer(123_456))

        val strict = OdometerStabilizer(requiredFrames = 2, maxGap = 1)
        strict.offer(123_456)
        strict.offer(null)
        strict.offer(null) // excede o limite: recomeça
        assertNull(strict.candidate)
        assertNull(strict.offer(123_456))
        assertEquals(123_456, strict.offer(123_456))
    }

    @Test
    fun `progress reflects the count`() {
        val stabilizer = OdometerStabilizer(requiredFrames = 4)
        assertEquals(0f, stabilizer.progress)
        stabilizer.offer(1_234)
        assertEquals(0.25f, stabilizer.progress)
        stabilizer.offer(1_234)
        assertEquals(0.5f, stabilizer.progress)
        stabilizer.reset()
        assertEquals(0f, stabilizer.progress)
    }
}
