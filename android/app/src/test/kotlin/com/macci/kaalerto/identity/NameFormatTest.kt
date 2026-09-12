package com.macci.kaalerto.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD §9's display form, and the containment around the surname. Ported from
 * feat/event-sourced-roles (roles dropped — this build has residents only).
 *
 * The surname is kept on the device so the display rule can change later, which means
 * the only thing standing between "Juan Dela Cruz" and every phone in the barangay is
 * that nothing ever puts it in an event. These tests are that guarantee written down.
 */
class NameFormatTest {

    @Test
    fun `a compound surname keeps the surname's initial`() {
        assertEquals("Juan D.", displayFormOf("Juan", "Dela Cruz"))
        assertEquals("Ana D.", displayFormOf("Ana", "De Guzman"))
        assertEquals("Mario S.", displayFormOf("Mario", "San Jose"))
    }

    @Test
    fun `a compound given name keeps the surname's initial, not its own`() {
        // The reason the two fields exist: as one string, "Juan Carlos Santos" would
        // render "Juan C." — an initial from the person's own given name.
        assertEquals("Juan Carlos S.", displayFormOf("Juan Carlos", "Santos"))
        assertEquals("Maria Cristina R.", displayFormOf("Maria Cristina", "Reyes"))
        assertEquals("John Paul B.", displayFormOf("John Paul", "Bautista"))
    }

    @Test
    fun `somebody with one name is not forced to invent a surname`() {
        assertEquals("Juan", displayFormOf("Juan", ""))
        assertEquals("Juan", displayFormOf("Juan", "   "))
    }

    @Test
    fun `whitespace never leaks into what the barangay sees`() {
        assertEquals("Juan D.", displayFormOf("  Juan  ", "  Dela Cruz  "))
        assertEquals("Juan Carlos S.", displayFormOf("Juan   Carlos", "Santos"))
        assertEquals("", displayFormOf("   ", "   "))
    }

    @Test
    fun `the initial is capitalised, the given name is left as typed`() {
        assertEquals("Juan D.", displayFormOf("Juan", "dela Cruz"))
        assertEquals("juan D.", displayFormOf("juan", "dela Cruz"))
    }

    @Test
    fun `only an empty given name is refused, and the surname is optional`() {
        // Deliberately weak: this is self-declared identification, never authentication.
        assertTrue(isUsableName("Juan"))
        assertTrue(isUsableName("x"))
        assertFalse(isUsableName(""))
        assertFalse(isUsableName("   "))
    }

    @Test
    fun `a barangay is required but never checked against a list`() {
        assertTrue(isUsableBarangay("San Juan Bautista"))
        assertTrue(isUsableBarangay("Barangay 5"))
        assertFalse(isUsableBarangay(""))
        assertFalse(isUsableBarangay("   "))
    }

    @Test
    fun `the display form never contains the surname`() {
        listOf("Dela Cruz", "Santos", "De Guzman", "Bautista").forEach { surname ->
            val display = displayFormOf("Juan", surname)
            surname.split(" ").forEach { word ->
                assertFalse(
                    "\"$display\" still carries \"$word\" — the surname must not reach an event",
                    display.contains(word, ignoreCase = true),
                )
            }
        }
    }

    /** displayName is what getOrCreate puts on Identity.authorName, which every submitter copies into the event. */
    @Test
    fun `the name embedded in an event is the display form`() {
        val embedded = LocalIdentity.displayName(suffix = "89A7", registered = displayFormOf("Juan", "Dela Cruz"))
        assertEquals("Juan D.", embedded)
        assertFalse(embedded.contains("Cruz"))
        assertFalse(embedded.contains("Dela"))
    }

    @Test
    fun `an unregistered device still gets the day-3 placeholder`() {
        assertEquals("Residente 89A7", LocalIdentity.displayName(suffix = "89A7", registered = ""))
        assertEquals("Residente 89A7", LocalIdentity.displayName(suffix = "89A7", registered = "   "))
    }
}
