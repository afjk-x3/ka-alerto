package com.macci.kaalerto.map

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePackActionTest {

    private val quiapo = 14.5995 to 120.9842
    private val dagupan = 16.0433 to 120.3333

    @Test
    fun `no recorded centre is a first build`() {
        // Also the case for a pack built before the centre was recorded: ensureDownloaded
        // adopts it as today, and the centre is recorded from then on.
        assertEquals(HomePackAction.FIRST_BUILD, homePackAction(builtCentre = null, newHome = quiapo))
    }

    @Test
    fun `a home moved away from the built centre rebuilds`() {
        assertEquals(HomePackAction.REBUILD, homePackAction(builtCentre = quiapo, newHome = dagupan))
    }

    @Test
    fun `saving the same home again keeps the pack it has`() {
        assertEquals(HomePackAction.KEEP, homePackAction(builtCentre = quiapo, newHome = quiapo))
    }

    @Test
    fun `any move rebuilds, so the pack is always centred on the saved home`() {
        // 0.0001 deg of latitude is ~11 m.
        assertEquals(HomePackAction.REBUILD, homePackAction(builtCentre = quiapo, newHome = 14.5996 to 120.9842))
    }
}
