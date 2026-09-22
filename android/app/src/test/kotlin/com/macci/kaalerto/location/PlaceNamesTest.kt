package com.macci.kaalerto.location

import com.macci.kaalerto.demo.isInDemoArea
import com.macci.kaalerto.demo.kmFromDemoArea
import com.macci.kaalerto.i18n.AppLanguage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNamesTest {

    /** The real bundled data — unit tests run from the module directory. */
    private val gazetteer = Gazetteer(
        streets = File("src/main/assets/routes").listFiles { f -> f.name.endsWith(".geojson") }!!
            .flatMap { parseStreets(it.readText()) },
        landmarks = parseLandmarks(File("src/main/assets/evacuation_centres.json").readText()),
    )

    private val area = "Brgy. Poblacion, Mapandan, Pangasinan"

    @Test
    fun `the bundled data parses to the real streets and centres, San Juan Bautista's retained alongside`() {
        val streets = File("src/main/assets/routes").listFiles()!!.flatMap { parseStreets(it.readText()) }
        assertEquals(
            setOf(
                "Sotto Street", "Josefa Llanes Escoda National Highway", "San Nicolas-Laoag Diversion Road",
                "Santa Barbara-Mangaldan Road", "Pandan Avenue",
            ),
            streets.map { it.name }.toSet(),
        )
        assertEquals(5, parseLandmarks(File("src/main/assets/evacuation_centres.json").readText()).size)
    }

    @Test
    fun `a point on a street is named by the street`() {
        assertEquals(PlaceName("Pandan Avenue", area), gazetteer.describe(16.0277229, 120.4544234))
    }

    @Test
    fun `a GPS fix a few tens of metres off the street still snaps to it`() {
        // ~17 m east of a Pandan Avenue vertex.
        assertEquals("Pandan Avenue", gazetteer.describe(16.0277229, 120.4547234)?.primary)
    }

    @Test
    fun `off every street but near a named building, it gives directions by the building`() {
        // ~167 m from Mapandan Catholic School, ~250 m from the nearest street — inside the landmark
        // radius, outside the street snap.
        assertEquals(PlaceName("Malapit sa Mapandan Catholic School", area), gazetteer.describe(16.0260, 120.4560))
    }

    @Test
    fun `off every street but near a named building, English says near`() {
        assertEquals(
            PlaceName("Near Mapandan Catholic School", area),
            gazetteer.describe(16.0260, 120.4560, AppLanguage.EN),
        )
    }

    @Test
    fun `inside the area but near nothing named, it falls back to the barangay`() {
        assertEquals(PlaceName("Brgy. Poblacion", "Mapandan, Pangasinan"), gazetteer.describe(16.040, 120.480))
    }

    @Test
    fun `outside the curated area it names nothing rather than guess`() {
        assertNull(gazetteer.describe(15.9761, 120.5711)) // Urdaneta, Pangasinan — real coverage, no curated content
        assertNull(gazetteer.describe(18.1709, 120.6058)) // San Juan Bautista — retained fixtures, no longer curated
    }

    @Test
    fun `curated-area bounds and distance`() {
        assertTrue(isInDemoArea(16.0256546, 120.4544745)) // Mapandan Catholic School
        assertFalse(isInDemoArea(15.9761, 120.5711)) // Urdaneta, Pangasinan
        assertFalse(isInDemoArea(18.1709, 120.6058)) // San Juan Bautista, retained but no longer curated
        assertEquals(1, kmFromDemoArea(16.0256546, 120.4544745))
        assertTrue(kmFromDemoArea(15.9761, 120.5711) in 1..15) // Urdaneta is inside the same province, close by
    }

    @Test
    fun `distance to a line is the perpendicular distance, clamped to its ends`() {
        val line = listOf(18.0 to 120.0, 18.0 to 120.001)
        assertEquals(0.0, distanceToLineMeters(18.0, 120.0005, line), 0.5)
        assertEquals(110.6, distanceToLineMeters(18.001, 120.0005, line), 1.0)
        assertEquals(Double.MAX_VALUE, distanceToLineMeters(18.0, 120.0, emptyList()), 0.0)
    }

    @Test
    fun `malformed data gives an empty list, not a crash`() {
        assertTrue(parseStreets("{nope").isEmpty())
        assertTrue(parseLandmarks("""{"centres": 3}""").isEmpty())
    }
}
