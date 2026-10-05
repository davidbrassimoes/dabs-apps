package pt.dabs.fueltracker.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NifTest {

    @Test
    fun `valid nifs`() {
        assertTrue(Nif.isValid("999999990")) // consumidor final
        assertTrue(Nif.isValid("123456789"))
        assertTrue(Nif.isValid("501964843")) // AT (Autoridade Tributária)
        assertTrue(Nif.isValid("500 697 256")) // Galp Energia, com espaços
    }

    @Test
    fun `invalid check digit`() {
        assertFalse(Nif.isValid("123456780"))
        assertFalse(Nif.isValid("999999991"))
    }

    @Test
    fun `wrong length or characters`() {
        assertFalse(Nif.isValid(""))
        assertFalse(Nif.isValid("12345678"))
        assertFalse(Nif.isValid("1234567890"))
        assertFalse(Nif.isValid("12345678a"))
        assertFalse(Nif.isValid("PT123456789"))
        assertFalse(Nif.isValid("012345674"))
    }

    @Test
    fun `normalize strips spaces and rejects non digits`() {
        assertEquals("123456789", Nif.normalize(" 123 456 789 "))
        assertNull(Nif.normalize("123-456"))
        assertNull(Nif.normalize(""))
    }
}
