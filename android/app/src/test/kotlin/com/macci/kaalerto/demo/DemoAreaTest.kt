package com.macci.kaalerto.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAreaTest {

    // Mapandan Catholic School, Brgy. Poblacion — the curated area's own centre.
    private val mapandan = 16.0256546 to 120.4544745

    // Dagupan City — elsewhere in Pangasinan, no curated content, real offline coverage.
    private val dagupan = 16.0433 to 120.3333

    // San Juan Bautista, Ilocos Norte — the old curated area, retained but no longer covered.
    private val sanJuanBautista = 18.1709 to 120.6058

    @Test
    fun `only the curated area itself is content-area`() {
        assertTrue(isInDemoArea(mapandan.first, mapandan.second))
        assertFalse(isInDemoArea(dagupan.first, dagupan.second))
        assertFalse(isInDemoArea(sanJuanBautista.first, sanJuanBautista.second))
    }

    @Test
    fun `offline coverage widens to the whole of Pangasinan, not further`() {
        assertTrue(isInOfflineCoverage(mapandan.first, mapandan.second))
        assertTrue(isInOfflineCoverage(dagupan.first, dagupan.second))
        assertFalse(isInOfflineCoverage(sanJuanBautista.first, sanJuanBautista.second))
    }

    @Test
    fun `every point that is in the content area is also in offline coverage`() {
        assertTrue(isInDemoArea(mapandan.first, mapandan.second) && isInOfflineCoverage(mapandan.first, mapandan.second))
    }
}
