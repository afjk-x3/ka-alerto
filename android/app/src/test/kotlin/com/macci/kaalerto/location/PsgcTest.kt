package com.macci.kaalerto.location

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the real bundled file, so a stale or broken `assets/psgc.json` fails here. */
class PsgcTest {

    private val psgc: Psgc = Psgc.parse(
        listOf("src/main/assets/psgc.json", "app/src/main/assets/psgc.json").map(::File).first { it.exists() }.readText(),
    )

    @Test
    fun `the bundled list is complete enough to trust`() {
        assertTrue("municipalities: ${psgc.municipalities.size}", psgc.municipalities.size > 1600)
        assertTrue(psgc.municipalities.all { psgc.barangays(it).isNotEmpty() })
    }

    @Test
    fun `the three San Nicolas stay apart by province`() {
        val labels = psgc.municipalities.filter { it.name == "San Nicolas" }.map { it.label }.toSet()
        assertEquals(setOf("San Nicolas, Batangas", "San Nicolas, Ilocos Norte", "San Nicolas, Pangasinan"), labels)
    }

    @Test
    fun `the demo area's municipality and barangay exist in the list`() {
        val m = psgc.find("San Nicolas, Ilocos Norte")
        assertNotNull(m)
        assertEquals("Brgy. San Juan Bautista", psgc.matchBarangay(m!!, "Brgy. San Juan Bautista"))
        assertEquals("Brgy. San Juan Bautista", psgc.matchBarangay(m, "san juan bautista"))
    }

    @Test
    fun `find ignores case, accents and spacing`() {
        assertEquals("Mapandan, Pangasinan", psgc.find("  MAPANDAN,   pangasinan ")?.label)
        assertNull(psgc.find("Mapandan"))
    }

    @Test
    fun `search finds by name or by name and province, starting-with first`() {
        assertEquals("Mapandan, Pangasinan", psgc.search("mapand").first().label)
        assertEquals("San Nicolas, Ilocos Norte", psgc.search("san nicolas ilocos").first().label)
        assertTrue(psgc.search("").isEmpty())
        assertTrue(psgc.search("zzzzzz").isEmpty())
    }

    @Test
    fun `cities are written the way people write them`() {
        assertNotNull(psgc.find("Laoag City, Ilocos Norte"))
        assertNotNull(psgc.find("Manila City, Metro Manila"))
    }

    @Test
    fun `a geocoder result maps to the municipality, however the geocoder spells it`() {
        assertEquals("Mapandan, Pangasinan", psgc.matchGeocoder("Mapandan", "Pangasinan", "Ilocos Region")?.label)
        assertEquals("Laoag City, Ilocos Norte", psgc.matchGeocoder("Laoag City", "Ilocos Norte", "Ilocos Region")?.label)
        assertEquals("Laoag City, Ilocos Norte", psgc.matchGeocoder("City of Laoag", null, "Ilocos Norte")?.label)
        assertEquals("Mapandan, Pangasinan", psgc.matchGeocoder(null, "Mapandan", "Pangasinan")?.label)
    }

    @Test
    fun `a same-named place is told apart by province, and left blank when it cannot be`() {
        assertEquals("San Nicolas, Ilocos Norte", psgc.matchGeocoder("San Nicolas", "Ilocos Norte", null)?.label)
        assertEquals("San Nicolas, Pangasinan", psgc.matchGeocoder("San Nicolas", null, "Pangasinan")?.label)
        assertNull(psgc.matchGeocoder("San Nicolas", null, "Region I"))
    }

    @Test
    fun `nothing that is not a place matches`() {
        assertNull(psgc.matchGeocoder("Atlantis", "Nowhere", "Nothing"))
        assertNull(psgc.matchGeocoder(null, null, null))
    }

    @Test
    fun `municipality suggestions come from the list with province, then places in use that the list lacks`() {
        val custom = com.macci.kaalerto.evac.EvacCentre(id = "x", name = "Hall", lat = 0.0, lon = 0.0, kind = "other", municipality = "Newtown, Nowhere")
        assertEquals("Mapandan, Pangasinan", com.macci.kaalerto.evac.suggestMunicipalities(emptyList(), null, "mapandan", psgc).first())
        assertTrue("Newtown, Nowhere" in com.macci.kaalerto.evac.suggestMunicipalities(listOf(custom), null, "newt", psgc))
        assertTrue(com.macci.kaalerto.evac.suggestMunicipalities(emptyList(), null, "Mapandan, Pangasinan", psgc).isEmpty())
    }

    @Test
    fun `barangay suggestions list the chosen municipality's barangays, and none before one is set`() {
        val all = com.macci.kaalerto.evac.suggestBarangays("San Nicolas, Ilocos Norte", emptyList(), null, null, "", psgc)
        assertEquals(8, all.size)
        assertEquals(listOf("Brgy. San Juan Bautista"), com.macci.kaalerto.evac.suggestBarangays("San Nicolas, Ilocos Norte", emptyList(), null, null, "juan", psgc))
        assertTrue(com.macci.kaalerto.evac.suggestBarangays("", emptyList(), null, null, "", psgc).isEmpty())
    }

    @Test
    fun `barangay search narrows by what was typed and drops the Pob marker`() {
        val m = psgc.find("San Nicolas, Ilocos Norte")!!
        assertTrue("Brgy. San Juan Bautista" in psgc.barangays(m))
        assertEquals(listOf("Brgy. San Juan Bautista"), psgc.searchBarangays(m, "juan"))
        assertEquals(8, psgc.searchBarangays(m, "").size)
        assertEquals(8, psgc.searchBarangays(m, "Brgy").size)
        assertEquals(8, psgc.searchBarangays(m, "barangay").size)
        assertEquals(listOf("Brgy. San Juan Bautista"), psgc.searchBarangays(m, "Brgy. san juan"))
    }
}
