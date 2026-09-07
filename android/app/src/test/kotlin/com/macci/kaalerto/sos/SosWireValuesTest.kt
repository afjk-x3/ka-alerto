package com.macci.kaalerto.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SOS context answers are not just labels — they are stored in `Event.payload`, they
 * go into the rescue card's QR, they ride the mesh, and day 12 will bit-pack them into
 * 160-character SMS. So what they are made of is a wire-format constraint, not a
 * typography preference.
 *
 * This exists because `PEOPLE_OPTIONS` used to be `"2–4"` with a U+2013 en dash, used
 * directly as the payload value. Three bytes in UTF-8, and not in the GSM 7-bit alphabet
 * at all — it would have broken day 12's packing on a field a rescuer actually reads, and
 * nothing would have caught it until the SMS leg was built.
 */
class SosWireValuesTest {

    private val storedOptionLists = mapOf(
        "PEOPLE_OPTIONS" to SosContext.PEOPLE_OPTIONS,
        "COMPANION_OPTIONS" to SosContext.COMPANION_OPTIONS,
        "MEDICAL_OPTIONS" to SosContext.MEDICAL_OPTIONS,
        "WATER_OPTIONS" to SosContext.WATER_OPTIONS,
        "TREND_OPTIONS" to SosContext.TREND_OPTIONS,
    )

    @Test
    fun `every stored answer is printable ASCII`() {
        storedOptionLists.forEach { (name, options) ->
            options.forEach { option ->
                option.forEach { ch ->
                    assertTrue(
                        "$name value \"$option\" contains U+%04X, which is not printable ASCII "
                            .format(ch.code) + "and cannot be assumed GSM 7-bit safe",
                        ch.code in 0x20..0x7E,
                    )
                }
            }
        }
    }

    @Test
    fun `the people label puts the en dash back for display only`() {
        assertEquals("2–4", SosContext.peopleLabel("2-4"))
        assertEquals("5–8", SosContext.peopleLabel("5-8"))
        // Values with no range are untouched.
        assertEquals("1", SosContext.peopleLabel("1"))
        assertEquals("9+", SosContext.peopleLabel("9+"))
    }

    @Test
    fun `the label is never what gets stored`() {
        SosContext.PEOPLE_OPTIONS.forEach { value ->
            val label = SosContext.peopleLabel(value)
            assertTrue(
                "peopleLabel(\"$value\") produced \"$label\", which is itself in PEOPLE_OPTIONS " +
                    "— the display form must not round-trip back in as a stored value",
                label == value || label !in SosContext.PEOPLE_OPTIONS,
            )
        }
    }
}
