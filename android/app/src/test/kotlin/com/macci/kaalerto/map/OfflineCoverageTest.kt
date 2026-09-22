package com.macci.kaalerto.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCoverageTest {

    private val dagupan = 16.0433 to 120.3333
    private val baguio = 16.4023 to 120.5960

    @Test
    fun `the curated area is always covered`() {
        // Mapandan Catholic School, Brgy. Poblacion — DemoArea.centre.
        assertTrue(isCovered(16.0256546, 120.4544745, homePackCentre = null, herePackCentre = null))
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
    fun `the Pangasinan pack counts only once it is actually ready`() {
        // Dagupan City, well inside the province but nowhere near the curated area or any home/here pack.
        assertFalse(isCovered(16.0433, 120.3333, homePackCentre = null, herePackCentre = null, pangasinanPackReady = false))
        assertTrue(isCovered(16.0433, 120.3333, homePackCentre = null, herePackCentre = null, pangasinanPackReady = true))
    }

    @Test
    fun `the Pangasinan pack does not cover a point genuinely outside its bounding box`() {
        // Manila — well south of the box, unlike Baguio above, which the rectangle actually
        // overshoots into (DemoArea.kt's own doc: the rectangle is bigger than the real province).
        assertFalse(isCovered(14.5995, 120.9842, homePackCentre = null, herePackCentre = null, pangasinanPackReady = true))
    }

    @Test
    fun `longitude extent widens with latitude, like boundsAround`() {
        // At 16 N a degree of longitude is ~107 km, so 0.0135 deg (~1.44 km) is inside.
        assertTrue(withinPack(16.0433, 120.3468, 16.0433, 120.3333))
        assertFalse(withinPack(16.0433, 120.3533, 16.0433, 120.3333))
    }
}
