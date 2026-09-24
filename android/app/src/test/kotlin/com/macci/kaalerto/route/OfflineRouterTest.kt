package com.macci.kaalerto.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs on the real bundled graph (assets/roadgraph.bin), so it also checks the file. */
class OfflineRouterTest {

    private val graph = parseRoadGraph(File("src/main/assets/roadgraph.bin").readBytes())!!
    private val mapandan = LatLon(16.0277, 120.4544)
    private val dagupan = LatLon(16.0433, 120.3333)

    @Test
    fun `a trip across Pangasinan finds a road route`() {
        val result = offlineRoutes(graph, mapandan, dagupan, emptyList())
        assertTrue(result is RoutesResult.Ok)
        val route = (result as RoutesResult.Ok).options.first()
        assertTrue(result.offline)
        // Straight line is ~13 km; by road it must be longer, but not absurdly so.
        assertTrue("${route.distanceM}", route.distanceM in 12_000.0..25_000.0)
    }

    @Test
    fun `a flood on the shortest route gets a route around it ranked first`() {
        val plain = (offlineRoutes(graph, mapandan, dagupan, emptyList()) as RoutesResult.Ok).options.first()
        // Put an impassable spot on the middle of the plain route.
        val (lon, lat) = plain.coords[plain.coords.size / 2]
        val result = offlineRoutes(graph, mapandan, dagupan, listOf(FloodPoint(lat, lon, "S3"))) as RoutesResult.Ok

        assertEquals(2, result.options.size)
        assertEquals(0, result.options.first().floodCount)
        assertTrue(result.options.first().safest)
        assertEquals(1, result.options.last().s3)
    }

    @Test
    fun `a trip outside Pangasinan says so`() {
        val manila = LatLon(14.5995, 120.9842)
        assertEquals(RoutesResult.OutsideOfflineMap, offlineRoutes(graph, mapandan, manila, emptyList()))
    }

    @Test
    fun `the graph is the Pangasinan road network`() {
        assertNotNull(graph)
        assertTrue(graph.size > 50_000)
    }
}
