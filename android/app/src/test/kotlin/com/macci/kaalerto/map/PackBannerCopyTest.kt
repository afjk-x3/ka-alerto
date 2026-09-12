package com.macci.kaalerto.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackBannerCopyTest {

    private fun downloading(completedTiles: Long, completedResources: Long = 0, requiredResources: Long? = null) =
        PackState.Downloading(
            completedTiles = completedTiles,
            completedResources = completedResources,
            completedBytes = 0,
            requiredResources = requiredResources,
            isPrecise = requiredResources != null,
        )

    /** The bug: a first run with no network sat at "Downloading offline map · 0%" forever. */
    @Test
    fun `a download that cannot start says it is waiting, not that it is at 0 percent`() {
        val copy = packBannerCopy(downloading(completedTiles = 0), isOnline = false)
        assertEquals("Naghihintay ng koneksyon", copy.headline)
        assertTrue(copy.detail!!.contains("Gumagana pa rin ang pag-uulat"))
        assertFalse("no bar under a stalled download", copy.showProgress)
    }

    @Test
    fun `a partial download that lost its connection reads as paused, with no moving bar`() {
        val copy = packBannerCopy(downloading(completedTiles = 3, completedResources = 40, requiredResources = 100), isOnline = false)
        assertEquals("Nakahinto ang download · 40%", copy.headline)
        assertTrue(copy.detail!!.contains("Itutuloy kapag may koneksyon"))
        assertFalse(copy.showProgress)
    }

    @Test
    fun `an online download shows its progress`() {
        val copy = packBannerCopy(downloading(completedTiles = 5, completedResources = 25, requiredResources = 100), isOnline = true)
        assertEquals("Dina-download ang mapa · 25%", copy.headline)
        assertEquals("5 tile", copy.detail)
        assertTrue(copy.showProgress)
    }

    @Test
    fun `an online download with no total yet says it is estimating`() {
        val copy = packBannerCopy(downloading(completedTiles = 0), isOnline = true)
        assertEquals("Dina-download ang mapa", copy.headline)
        assertEquals("0 tile (tinatantiya ang kabuuan)", copy.detail)
        assertTrue(copy.showProgress)
    }

    @Test
    fun `no pack and no network says a connection is needed once`() {
        val copy = packBannerCopy(PackState.Absent, isOnline = false)
        assertEquals("Wala pang offline na mapa", copy.headline)
        assertTrue(copy.detail!!.startsWith("Kailangan ng koneksyon nang isang beses."))
    }

    @Test
    fun `no pack with a network says the download is starting`() {
        assertEquals("Sinisimulan ang download.", packBannerCopy(PackState.Absent, isOnline = true).detail)
    }

    @Test
    fun `a failure keeps its reason`() {
        val copy = packBannerCopy(PackState.Failed("Tile limit exceeded (6000)"), isOnline = true)
        assertEquals("Hindi na-download ang mapa", copy.headline)
        assertEquals("Tile limit exceeded (6000)", copy.detail)
        assertFalse(copy.showProgress)
    }
}
