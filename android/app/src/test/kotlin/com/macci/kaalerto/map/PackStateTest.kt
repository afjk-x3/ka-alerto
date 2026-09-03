package com.macci.kaalerto.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackStateTest {

    private fun downloading(
        completedResources: Long,
        requiredResources: Long?,
        completedTiles: Long = 0,
    ) = PackState.Downloading(
        completedTiles = completedTiles,
        completedResources = completedResources,
        completedBytes = 0,
        requiredResources = requiredResources,
        isPrecise = true,
    )

    @Test
    fun `fraction is unknown until MapLibre reports a total`() {
        assertNull(downloading(completedResources = 40, requiredResources = null).fraction)
    }

    @Test
    fun `fraction is unknown when the reported total is zero`() {
        assertNull(downloading(completedResources = 0, requiredResources = 0).fraction)
    }

    @Test
    fun `fraction is completed over required resources`() {
        assertEquals(0.25f, downloading(completedResources = 25, requiredResources = 100).fraction!!, 0.0001f)
    }

    /**
     * The denominator counts tiles plus style, glyphs and sprites, so it must not be
     * compared against the tile count alone — that understates progress and makes the
     * bar look stuck.
     */
    @Test
    fun `fraction ignores the tile count`() {
        val state = downloading(completedResources = 50, requiredResources = 100, completedTiles = 5)
        assertEquals(0.5f, state.fraction!!, 0.0001f)
    }

    /** MapLibre can report more completed than required while the estimate is still coarse. */
    @Test
    fun `fraction never exceeds one`() {
        assertEquals(1f, downloading(completedResources = 150, requiredResources = 100).fraction!!, 0.0001f)
    }
}
