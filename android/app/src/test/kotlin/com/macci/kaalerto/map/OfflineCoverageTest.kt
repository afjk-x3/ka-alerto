package com.macci.kaalerto.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCoverageTest {

    private val dagupan = 16.0433 to 120.3333
    private val baguio = 16.4023 to 120.5960

    @Test
    fun `the demo area is always covered`() {
        assertTrue(isCovered(18.1709, 120.6058, homePackCentre = null, herePackCentre = null))
    }

    @Test
    fun `a ready home pack covers about 1_5 km each way and no further`() {
        // 0.0100 deg of latitude is ~1.1 km; 0.0200 is ~2.2 km.
        assertTrue(isCovered(16.0533, 120.3333, homePackCentre = dagupan, herePackCentre = null))
        assertFalse(isCovered(16.0633, 120.3333, homePackCentre = dagupan, herePackCentre = null))
    }

    @Test
    fun `a ready here pack covers its own spot`() {
        assertTrue(isCovered(16.4023, 120.5960, homePackCentre = dagupan, herePackCentre = baguio))
    }

    @Test
    fun `somewhere with no pack is not covered`() {
        assertFalse(isCovered(16.4023, 120.5960, homePackCentre = dagupan, herePackCentre = null))
        assertFalse(isCovered(16.4023, 120.5960, homePackCentre = null, herePackCentre = null))
    }

    @Test
    fun `longitude extent widens with latitude, like boundsAround`() {
        // At 16 N a degree of longitude is ~107 km, so 0.0135 deg (~1.44 km) is inside.
        assertTrue(withinPack(16.0433, 120.3468, 16.0433, 120.3333))
        assertFalse(withinPack(16.0433, 120.3533, 16.0433, 120.3333))
    }
}
