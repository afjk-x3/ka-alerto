package com.macci.kaalerto.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedRoutesTest {

    // Pandan Avenue-ish, west to east, (lon, lat).
    private val route = SavedRoute("r1", "Mapandan Catholic School", listOf(120.4500 to 16.0270, 120.4560 to 16.0280))

    @Test
    fun `a report on the route line is on it`() {
        assertEquals("r1", savedRouteNear(16.0275, 120.4530, listOf(route))?.id)
    }

    @Test
    fun `a report a block away is not`() {
        // ~150 m north of the line.
        assertNull(savedRouteNear(16.0289, 120.4530, listOf(route)))
    }

    @Test
    fun `thinning keeps the ends and stays under the cap`() {
        val long = (0 until 1_000).map { 120.0 + it * 0.0001 to 16.0 }
        val thinned = thin(long)
        assertEquals(long.first(), thinned.first())
        assertEquals(long.last(), thinned.last())
        assert(thinned.size <= 401)
    }
}
