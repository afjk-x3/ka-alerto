package com.macci.kaalerto.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceCacheTest {

    private fun place(lat: Double, lon: Double, label: String, at: Long = 0L) = CachedPlace(lat, lon, label, at)

    private val dagupan = place(16.0433, 120.3333, "Poblacion, Dagupan")

    @Test
    fun `a name within 1 km is found`() {
        // 0.0027 deg of latitude is ~300 m.
        assertEquals(dagupan, nearestCached(listOf(dagupan), 16.0460, 120.3333))
    }

    @Test
    fun `a name further than 1 km is not`() {
        // 0.0180 deg is ~2 km.
        assertNull(nearestCached(listOf(dagupan), 16.0613, 120.3333))
    }

    @Test
    fun `the nearest of several wins`() {
        val near = place(16.0450, 120.3333, "Near one")
        val far = place(16.0500, 120.3333, "Far one")
        assertEquals(near, nearestCached(listOf(far, near), 16.0445, 120.3333))
    }

    @Test
    fun `an empty cache names nothing`() {
        assertNull(nearestCached(emptyList(), 16.0433, 120.3333))
    }

    @Test
    fun `the cache keeps the newest 200 and drops the oldest`() {
        // 0.001 deg apart (~111 m), so no entry replaces another.
        var entries = emptyList<CachedPlace>()
        for (i in 0 until 205) entries = withCached(entries, place(16.0 + i * 0.001, 120.0, "p$i", i.toLong()))
        assertEquals(200, entries.size)
        assertEquals("p5", entries.first().label)
        assertEquals("p204", entries.last().label)
    }

    @Test
    fun `looking up the same spot again replaces the old entry instead of piling up`() {
        val first = place(16.0433, 120.3333, "Old name", 1)
        val again = place(16.0435, 120.3333, "New name", 2) // ~22 m away
        assertEquals(listOf(again), withCached(listOf(first), again))
    }
}
