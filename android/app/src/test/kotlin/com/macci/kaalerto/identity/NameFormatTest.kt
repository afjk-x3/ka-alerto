package com.macci.kaalerto.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD §9's display form, and the containment around the full name.
 *
 * The decision (7 Sep) was to keep the typed name on the device rather than only the
 * derived short form. That is defensible — it lets the display rule change later — but
 * it means the only thing standing between "Juan Dela Cruz" and every phone in the
 * barangay is that nothing ever puts it in an event. These tests are that guarantee
 * written down, because a comment is not one.
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
        // The reason the two fields exist. As one string with the initial taken from the
        // second word, this person rendered "Juan C." — an initial from their own given
        // name. No rule over a single string can separate "Juan Carlos Santos" from
        // "Juan Dela Cruz"; both are ordinary names here.
        assertEquals("Juan Carlos S.", displayFormOf("Juan Carlos", "Santos"))
        assertEquals("Maria Cristina R.", displayFormOf("Maria Cristina", "Reyes"))
        assertEquals("John Paul B.", displayFormOf("John Paul", "Bautista"))
    }

    @Test
    fun `a simple name is unaffected`() {
        assertEquals("Maria S.", displayFormOf("Maria", "Santos"))
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
        // The initial is normalised because "juan d." reads as a typo rather than a name.
        assertEquals("Juan D.", displayFormOf("Juan", "dela Cruz"))
        // The given name is not. The field auto-capitalises words, so lowercase input is
        // deliberate — and an app that silently re-cases somebody's name will eventually
        // be wrong about a name that is genuinely styled that way.
        assertEquals("juan D.", displayFormOf("juan", "dela Cruz"))
    }

    @Test
    fun `only an empty given name is refused, and the surname is optional`() {
        // Deliberately weak. Nothing here verifies anybody — the decision table calls
        // this self-declared identification, never authentication — and a validator that
        // decides what counts as a real Filipino name will be wrong about somebody's.
        assertTrue(isUsableName("Juan"))
        assertTrue(isUsableName("x"))
        assertFalse(isUsableName(""))
        assertFalse(isUsableName("   "))
    }

    // ---- containment ----

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

    @Test
    fun `the name embedded in an event is the display form, in every role`() {
        // displayName is what LocalIdentity.getOrCreate puts on Identity.authorName, and
        // Identity.authorName is what every submitter copies into Event.authorName. So
        // this is the actual boundary the surname must not cross.
        val display = displayFormOf("Juan", "Dela Cruz")
        listOf(
            LocalIdentity.ROLE_RESIDENT,
            LocalIdentity.ROLE_RESPONDER,
            LocalIdentity.ROLE_OFFICIAL,
        ).forEach { role ->
            val embedded = LocalIdentity.displayName(role, "89A7", display)
            assertTrue("$role lost the name", embedded.contains("Juan D."))
            assertFalse("$role leaked the surname: $embedded", embedded.contains("Cruz"))
            assertFalse("$role leaked the surname: $embedded", embedded.contains("Dela"))
        }
    }

    @Test
    fun `an unregistered device still gets the day-3 placeholder`() {
        assertEquals("Residente 89A7", LocalIdentity.displayName(LocalIdentity.ROLE_RESIDENT, "89A7"))
        assertEquals("Kagawad 89A7", LocalIdentity.displayName(LocalIdentity.ROLE_OFFICIAL, "89A7"))
    }

    @Test
    fun `a registered official is attributable as an official`() {
        // OfficialVerify.dc.html signs a ruling "M. Reyes, Kagawad" — the role has to
        // ride along, or an official act is indistinguishable from a resident's.
        assertEquals(
            "Kagawad Juan D.",
            LocalIdentity.displayName(LocalIdentity.ROLE_OFFICIAL, "89A7", "Juan D."),
        )
        assertEquals(
            "Juan D.",
            LocalIdentity.displayName(LocalIdentity.ROLE_RESIDENT, "89A7", "Juan D."),
        )
    }
}
