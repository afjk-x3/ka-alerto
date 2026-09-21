package com.macci.kaalerto.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Route ranking is a port of dashboard/src/lib/routing.ts; these pin the same rules. */
class RoutingTest {

    private val to = LatLon(16.0300, 120.4200)

    /** A straight north-south route along `lon`, from lat 16.0200 to the destination's. */
    private fun route(lon: Double, durationS: Double = 300.0) = RawRoute(
        coords = listOf(lon to 16.0200, lon to 16.0250, lon to 16.0300),
        distanceM = 1_100.0,
        durationS = durationS,
    )

    private fun flood(lat: Double, lon: Double, sev: String = "S3") = FloodPoint(lat, lon, sev)

    @Test
    fun `the route with fewer floods ranks first and is marked safest`() {
        val flooded = route(120.4200)
        val clear = route(120.4230) // ~330 m east, well outside 75 m
        val ranked = rankRoutes(listOf(flooded, clear), listOf(flood(16.0225, 120.4200)), to)

        assertEquals(120.4230, ranked[0].coords[0].first, 0.0)
        assertTrue(ranked[0].safest)
        assertFalse(ranked[1].safest)
        assertEquals(1, ranked[1].s3)
    }

    @Test
    fun `a flood at the destination is the reason for going, not an obstacle`() {
        val ranked = rankRoutes(listOf(route(120.4200)), listOf(flood(16.0300, 120.4200)), to)
        assertEquals(0, ranked[0].floodCount)
    }

    @Test
    fun `a flood 75 m or more from the line does not count`() {
        // 0.001 deg lon at lat 16 is about 107 m.
        val ranked = rankRoutes(listOf(route(120.4200)), listOf(flood(16.0225, 120.4210)), to)
        assertEquals(0, ranked[0].floodCount)
    }

    @Test
    fun `S3 outweighs S2 outweighs conflicting when counts tie on number`() {
        val a = route(120.4200)
        val b = route(120.4230)
        val ranked = rankRoutes(listOf(a, b), listOf(flood(16.0225, 120.4200, "S3"), flood(16.0225, 120.4230, "S2")), to)
        assertEquals(1, ranked[0].s2) // the S2 route scores 2, the S3 route scores 3
    }

    @Test
    fun `equal flood scores fall back to the quicker route`() {
        val slow = route(120.4200, durationS = 900.0)
        val quick = route(120.4230, durationS = 300.0)
        val ranked = rankRoutes(listOf(slow, quick), emptyList(), to)
        assertEquals(300.0, ranked[0].durationS, 0.0)
    }

    @Test
    fun `parseOsrm reads the routes and rejects anything not Ok`() {
        val ok = """{"code":"Ok","routes":[{"distance":1234.5,"duration":99.0,"geometry":{"coordinates":[[120.4,16.0],[120.5,16.1]]},"legs":[]}]}"""
        val parsed = parseOsrm(ok)!!
        assertEquals(1, parsed.size)
        assertEquals(120.4 to 16.0, parsed[0].coords[0])
        assertEquals(1234.5, parsed[0].distanceM, 0.0)

        assertNull(parseOsrm("""{"code":"NoRoute"}"""))
        assertNull(parseOsrm("not json"))
    }
}
