package com.macci.kaalerto.sos

import com.macci.kaalerto.demo.DemoArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoldPlaceLineTest {

    private val sottoStreet = 18.1709 to 120.6058
    private val quiapo = 14.5995 to 120.9842

    @Test
    fun `a place label is shown alone, never with the demo barangay appended`() {
        // The gazetteer label already names the barangay; appending it again read
        // "..., Brgy. San Juan Bautista, ... · Brgy. San Juan Bautista".
        val label = "Sotto Street, Brgy. San Juan Bautista, San Nicolas, Ilocos Norte"
        assertEquals(label, holdPlaceLine(label, sottoStreet.first, sottoStreet.second))
    }

    @Test
    fun `an out-of-area label is not paired with the demo barangay`() {
        assertEquals("Malapit sa Quiapo, Manila", holdPlaceLine("Malapit sa Quiapo, Manila", quiapo.first, quiapo.second))
    }

    @Test
    fun `no label inside the demo area falls back to the demo barangay`() {
        assertEquals(DemoArea.BARANGAY_NAME, holdPlaceLine(null, sottoStreet.first, sottoStreet.second))
    }

    @Test
    fun `no label outside the demo area claims no place at all`() {
        assertNull(holdPlaceLine(null, quiapo.first, quiapo.second))
    }
}
