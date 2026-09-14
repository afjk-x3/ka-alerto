package com.macci.kaalerto.map

import com.macci.kaalerto.geofence.HomeLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartCameraTest {

    @Test
    fun `no home opens on the demo area`() {
        assertNull(homeStart(null))
    }

    @Test
    fun `a home inside the demo area still opens on the demo area`() {
        // Demo phones: the scripted demo must not move.
        assertNull(homeStart(HomeLocation(18.1709, 120.6058, 300.0)))
    }

    @Test
    fun `a home outside the demo area opens on the home`() {
        assertEquals(16.0433 to 120.3333, homeStart(HomeLocation(16.0433, 120.3333, 300.0)))
    }
}
