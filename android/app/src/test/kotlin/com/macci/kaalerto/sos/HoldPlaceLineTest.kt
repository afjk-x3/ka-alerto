package com.macci.kaalerto.sos

import com.macci.kaalerto.demo.DemoArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoldPlaceLineTest {

    private val pandanAvenue = 16.0277229 to 120.4544234 // inside the curated area (Mapandan, Pangasinan)
    private val quiapo = 14.5995 to 120.9842

    @Test
    fun `a place label is shown alone, never with the curated barangay appended`() {
        // The gazetteer label already names the barangay; appending it again read
        // "..., Brgy. Poblacion, ... · Brgy. Poblacion".
        val label = "Pandan Avenue, Brgy. Poblacion, Mapandan, Pangasinan"
        assertEquals(label, holdPlaceLine(label, pandanAvenue.first, pandanAvenue.second))
    }

    @Test
    fun `an out-of-area label is not paired with the curated barangay`() {
        assertEquals("Malapit sa Quiapo, Manila", holdPlaceLine("Malapit sa Quiapo, Manila", quiapo.first, quiapo.second))
    }

    @Test
    fun `no label inside the curated area falls back to the curated barangay`() {
        assertEquals(DemoArea.BARANGAY_NAME, holdPlaceLine(null, pandanAvenue.first, pandanAvenue.second))
    }

    @Test
    fun `no label outside the curated area claims no place at all`() {
        assertNull(holdPlaceLine(null, quiapo.first, quiapo.second))
    }
}
