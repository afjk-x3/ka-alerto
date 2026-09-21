package com.macci.kaalerto.evac

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Officials adding shelters and opening or closing them, scoped by municipality (the fold, not the UI). */
class EvacCentresTest {

    private val now = 1_700_000_000_000L
    private val sanNicolas = "San Nicolas, Ilocos Norte"
    private val laoag = "Laoag City, Ilocos Norte"

    private val bundled = listOf(
        EvacCentre(
            id = "evac-bundled", name = "Filipinas East ES", lat = 18.1688, lon = 120.6059, kind = "school",
            capacityEstimate = 180, municipality = sanNicolas, barangay = "Brgy. San Juan Bautista",
        ),
    )

    private val official = LocalIdentity.Identity(authorId = "off-1", authorName = "Kagawad A1B2", authorRole = LocalIdentity.ROLE_OFFICIAL)

    private fun centre(id: String, minutesAgo: Long = 10, name: String = "Barangay Hall", municipality: String = sanNicolas, removed: Boolean = false, author: String = "off-1") =
        newEvacCentreEvent(
            official.copy(authorId = author),
            EvacCentrePayload(centreId = id, name = name, kind = "barangay_hall", lat = 18.17, lon = 120.6, municipality = municipality, barangay = "Brgy. Sotto", removed = removed),
            now - minutesAgo * 60_000,
        ).copy(id = "c-$id-$minutesAgo-$municipality-$removed-$name")

    private fun status(centreId: String, status: EvacStatus, minutesAgo: Long = 5, municipality: String? = sanNicolas, occupancy: Int? = null) = Event(
        id = "s-$centreId-$minutesAgo-$status-$municipality", type = TYPE_EVAC_STATUS, lat = 0.0, lon = 0.0, featureRef = null, severity = null, waterLevel = null,
        authorId = "off-1", authorName = "Kagawad A1B2", authorRole = "official", timestampMs = now - minutesAgo * 60_000, expiresAt = now + 3_600_000,
        origin = "local", hopCount = 0, note = null,
        payload = EvacPayload(centreId, status.key, occupancy, municipality).encode(),
    )

    private fun states(vararg events: Event) = evacStates(bundled, events.toList(), null, null)
    private fun byId(list: List<EvacState>, id: String) = list.firstOrNull { it.centre.id == id }

    // ---- adding ----

    @Test
    fun `with no events only the bundled shelters exist, all not open yet`() {
        val list = states()
        assertEquals(listOf("evac-bundled"), list.map { it.centre.id })
        assertEquals(EvacStatus.NOT_OPEN, list[0].status)
    }

    @Test
    fun `an added shelter appears with its municipality and barangay, closed by default`() {
        val s = byId(states(centre("evac-new")), "evac-new")!!
        assertEquals("Barangay Hall", s.centre.name)
        assertEquals(sanNicolas, s.centre.municipality)
        assertEquals("Brgy. Sotto", s.centre.barangay)
        assertTrue(s.centre.custom)
        assertEquals(EvacStatus.NOT_OPEN, s.status)
    }

    @Test
    fun `a later event from the same municipality edits it, the latest wins`() {
        val s = byId(states(centre("evac-new", minutesAgo = 20, name = "Old name"), centre("evac-new", minutesAgo = 5, name = "New name")), "evac-new")!!
        assertEquals("New name", s.centre.name)
    }

    @Test
    fun `arrival order does not change the result`() {
        val a = centre("evac-new", minutesAgo = 20, name = "Old name")
        val b = centre("evac-new", minutesAgo = 5, name = "New name")
        assertEquals(states(a, b).map { it.centre }, states(b, a).map { it.centre })
    }

    @Test
    fun `another municipality cannot rewrite or remove a shelter it did not create`() {
        val created = centre("evac-new", minutesAgo = 20, name = "Original")
        val hijack = centre("evac-new", minutesAgo = 5, name = "Hijacked", municipality = laoag)
        val wipe = centre("evac-new", minutesAgo = 4, municipality = laoag, removed = true)
        assertEquals("Original", byId(states(created, hijack, wipe), "evac-new")!!.centre.name)
    }

    @Test
    fun `the same municipality spelled differently is still the same municipality`() {
        val created = centre("evac-new", minutesAgo = 20, name = "Original")
        val edit = centre("evac-new", minutesAgo = 5, name = "Edited", municipality = "  SAN  NICOLAS, ilocos norte ")
        assertEquals("Edited", byId(states(created, edit), "evac-new")!!.centre.name)
    }

    // ---- removing ----

    @Test
    fun `a removed shelter disappears`() {
        val list = states(centre("evac-new", minutesAgo = 20), centre("evac-new", minutesAgo = 5, removed = true))
        assertNull(byId(list, "evac-new"))
    }

    @Test
    fun `a bundled shelter cannot be removed or replaced by an event`() {
        val list = states(centre("evac-bundled", removed = true), centre("evac-bundled", name = "Renamed"))
        assertEquals("Filipinas East ES", byId(list, "evac-bundled")!!.centre.name)
    }

    @Test
    fun `an event with no name or municipality is ignored`() {
        assertNull(byId(states(centre("evac-x", name = "  ")), "evac-x"))
        assertNull(byId(states(centre("evac-y", municipality = "")), "evac-y"))
    }

    // ---- opening and closing (status), scoped ----

    @Test
    fun `an official of the shelter's municipality can open it`() {
        val list = states(centre("evac-new"), status("evac-new", EvacStatus.ACCEPTING, occupancy = 12))
        val s = byId(list, "evac-new")!!
        assertEquals(EvacStatus.ACCEPTING, s.status)
        assertEquals(12, s.occupancy)
    }

    @Test
    fun `an update stamped with another municipality is ignored`() {
        val list = states(centre("evac-new"), status("evac-new", EvacStatus.ACCEPTING, municipality = laoag))
        assertEquals(EvacStatus.NOT_OPEN, byId(list, "evac-new")!!.status)
    }

    @Test
    fun `an older update with no municipality is still accepted`() {
        val list = states(status("evac-bundled", EvacStatus.ACCEPTING, municipality = null))
        assertEquals(EvacStatus.ACCEPTING, byId(list, "evac-bundled")!!.status)
    }

    @Test
    fun `a status for a shelter that does not exist, or was removed, changes nothing`() {
        val removed = states(centre("evac-new", minutesAgo = 30), centre("evac-new", minutesAgo = 20, removed = true), status("evac-new", EvacStatus.ACCEPTING))
        assertNull(byId(removed, "evac-new"))
        assertEquals(listOf("evac-bundled"), states(status("evac-ghost", EvacStatus.ACCEPTING)).map { it.centre.id })
    }

    @Test
    fun `close then open again ends open, the latest update wins`() {
        val list = states(
            status("evac-bundled", EvacStatus.ACCEPTING, minutesAgo = 30),
            status("evac-bundled", EvacStatus.NOT_OPEN, minutesAgo = 20),
            status("evac-bundled", EvacStatus.ACCEPTING, minutesAgo = 10),
        )
        assertEquals(EvacStatus.ACCEPTING, byId(list, "evac-bundled")!!.status)
    }

    // ---- who may manage ----

    @Test
    fun `an official manages only their own municipality's shelters, and none if theirs is unset`() {
        val c = bundled[0]
        assertTrue(canManage(sanNicolas, c))
        assertTrue(canManage(" san nicolas, ilocos NORTE", c))
        assertFalse(canManage(laoag, c))
        assertFalse(canManage("", c))
        assertFalse(canManage(null, c))
    }

    // ---- suggestions ----

    @Test
    fun `municipality suggestions come from shelters and this phone, one spelling each`() {
        val centres = bundled + bundled[0].copy(id = "b", municipality = "san nicolas, ilocos norte") + bundled[0].copy(id = "c", municipality = laoag)
        val all = suggestMunicipalities(centres, own = "Batac City, Ilocos Norte", query = "")
        assertEquals(setOf(sanNicolas, laoag, "Batac City, Ilocos Norte"), all.toSet())
        assertEquals(3, all.size)
    }

    @Test
    fun `typing narrows suggestions, starting-with first, and never repeats what is already typed`() {
        val centres = bundled + bundled[0].copy(id = "c", municipality = laoag)
        assertEquals(listOf(sanNicolas), suggestMunicipalities(centres, null, "san"))
        assertEquals(listOf(sanNicolas), suggestMunicipalities(centres, null, "nicol"))
        assertTrue(suggestMunicipalities(centres, null, sanNicolas).isEmpty())
    }

    @Test
    fun `barangay suggestions are limited to the chosen municipality and empty until one is set`() {
        val centres = bundled + bundled[0].copy(id = "c", municipality = laoag, barangay = "Brgy. 1")
        assertEquals(listOf("Brgy. San Juan Bautista"), suggestBarangays(sanNicolas, centres, null, null, ""))
        assertEquals(listOf("Brgy. 1"), suggestBarangays(laoag, centres, null, null, ""))
        assertTrue(suggestBarangays("", centres, null, null, "").isEmpty())
        assertTrue(suggestBarangays(null, centres, null, null, "").isEmpty())
    }

    @Test
    fun `this phone's own barangay is suggested only under its own municipality`() {
        assertTrue("Brgy. Home" in suggestBarangays(laoag, emptyList(), ownMunicipality = laoag, ownBarangay = "Brgy. Home", query = ""))
        assertTrue(suggestBarangays(sanNicolas, emptyList(), ownMunicipality = laoag, ownBarangay = "Brgy. Home", query = "").isEmpty())
    }

    @Test
    fun `a walking time is given only for a distance someone could walk`() {
        assertEquals(11, walkingMinutes(750.0))
        assertEquals(75, walkingMinutes(5_000.0))
        assertNull(walkingMinutes(5_001.0))
        assertNull(walkingMinutes(238_900.0))
    }
}
