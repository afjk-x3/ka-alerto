package com.macci.kaalerto.map

import com.macci.kaalerto.geofence.HomeLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartCameraTest {

    @Test
    fun `no home opens on the curated area`() {
        assertNull(homeStart(null))
    }

    @Test
    fun `a home inside the curated area still opens on the curated area`() {
        // Demo phones: the scripted demo must not move. Mapandan Catholic School, Brgy. Poblacion.
        assertNull(homeStart(HomeLocation(16.0256546, 120.4544745, 300.0)))
    }

    @Test
    fun `a home elsewhere in Pangasinan also opens on the curated area, since it now has real coverage`() {
        // Dagupan City — nowhere near the curated area, but inside the province-wide pack (22 Sep 2026).
        assertNull(homeStart(HomeLocation(16.0433, 120.3333, 300.0)))
    }

    @Test
    fun `a home with no real coverage at all opens on the home`() {
        // San Juan Bautista, Ilocos Norte — the old curated area. Its own reports are retained, but it
        // has no offline pack any more, so a home here is exactly the "not covered" case this guards.
        assertEquals(18.1709 to 120.6058, homeStart(HomeLocation(18.1709, 120.6058, 300.0)))
    }
}
