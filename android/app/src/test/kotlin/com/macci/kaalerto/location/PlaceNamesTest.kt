package com.macci.kaalerto.location

import com.macci.kaalerto.demo.isInDemoArea
import com.macci.kaalerto.demo.kmFromDemoArea
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

    private val area = "Brgy. San Juan Bautista, San Nicolas, Ilocos Norte"

    @Test
    fun `the bundled data parses to the three real streets and four centres`() {
        val streets = File("src/main/assets/routes").listFiles()!!.flatMap { parseStreets(it.readText()) }
        assertEquals(
            setOf("Sotto Street", "Josefa Llanes Escoda National Highway", "San Nicolas-Laoag Diversion Road"),
            streets.map { it.name }.toSet(),
        )
        assertEquals(4, parseLandmarks(File("src/main/assets/evacuation_centres.json").readText()).size)
    }

    @Test
    fun `a point on a street is named by the street`() {
        // seed-report-001, which sits on the Sotto Street centreline.
        assertEquals(PlaceName("Sotto Street", area), gazetteer.describe(18.169942, 120.6052042))
    }

    @Test
    fun `a GPS fix a few tens of metres off the street still snaps to it`() {
        // ~30 m east of seed-report-003, mid-street — not 001, which sits at the junction
        // with the highway, where "nearest street" rightly flips to the highway.
        assertEquals("Sotto Street", gazetteer.describe(18.1715238, 120.606375)?.primary)
    }

    @Test
    fun `off every street but near a named building, it gives directions by the building`() {
        assertEquals(PlaceName("Malapit sa San Nicolas National High School", area), gazetteer.describe(18.17036, 120.61090))
    }

    @Test
    fun `inside the area but near nothing named, it falls back to the barangay`() {
        assertEquals(PlaceName("Brgy. San Juan Bautista", "San Nicolas, Ilocos Norte"), gazetteer.describe(18.1755, 120.5995))
    }

    @Test
    fun `outside the demo area it names nothing rather than guess`() {
        // Urdaneta, Pangasinan.
        assertNull(gazetteer.describe(15.9761, 120.5711))
    }

    @Test
    fun `demo area bounds and distance`() {
        assertTrue(isInDemoArea(18.1709, 120.6058))
        assertFalse(isInDemoArea(15.9761, 120.5711))
        assertFalse(isInDemoArea(18.1709, 120.5900)) // ~1 km west: San Nicolas poblacion, excluded on purpose
        assertTrue(kmFromDemoArea(15.9761, 120.5711) in 240..250)
        assertEquals(1, kmFromDemoArea(18.1709, 120.6058))
    }

    @Test
    fun `distance to a line is the perpendicular distance, clamped to its ends`() {
        val line = listOf(18.0 to 120.0, 18.0 to 120.001) // ~106 m east-west segment
        assertEquals(0.0, distanceToLineMeters(18.0, 120.0005, line), 0.5)
        assertEquals(110.6, distanceToLineMeters(18.001, 120.0005, line), 1.0)
        assertEquals(Double.MAX_VALUE, distanceToLineMeters(18.0, 120.0, emptyList()), 0.0)
    }

    @Test
    fun `geocoder parts drop blanks and repeats, most specific first`() {
        assertEquals(
            PlaceName("Nancalobasaan", "Urdaneta, Pangasinan"),
            placeNameFromParts("Nancalobasaan", "Urdaneta", "Pangasinan", "Ilocos Region"),
        )
        assertEquals(PlaceName("Urdaneta", "Pangasinan"), placeNameFromParts(null, "Urdaneta", " Urdaneta ", "Pangasinan"))
        assertEquals(PlaceName("Pangasinan", null), placeNameFromParts("", null, "Pangasinan", null))
        assertNull(placeNameFromParts(null, " ", null, null))
    }

    @Test
    fun `malformed data gives an empty list, not a crash`() {
        assertTrue(parseStreets("{nope").isEmpty())
        assertTrue(parseLandmarks("""{"centres": 3}""").isEmpty())
    }
}
