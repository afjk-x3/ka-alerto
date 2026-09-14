package com.macci.kaalerto.detail

import com.macci.kaalerto.i18n.AppLanguage
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportLabelsTest {

    // Tests fix the zone so they pass on any machine; the app uses the phone's zone.
    private val manila = ZoneId.of("Asia/Manila")
    private val fil = AppLanguage.FIL
    private val en = AppLanguage.EN

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(manila).toInstant().toEpochMilli()

    private val now = at(2026, 9, 12, 10, 45)

    @Test
    fun `a report from earlier today says today`() {
        assertEquals("Ngayong araw, 10:36 AM", reportedAtLabel(at(2026, 9, 12, 10, 36), fil, now, manila))
        assertEquals("Today, 10:36 AM", reportedAtLabel(at(2026, 9, 12, 10, 36), en, now, manila))
    }

    /** Calendar day, not "within 24 hours" — 11:50 PM last night is yesterday, even 11 hours on. */
    @Test
    fun `a report from the previous calendar day says yesterday`() {
        assertEquals("Kahapon, 11:50 PM", reportedAtLabel(at(2026, 9, 11, 23, 50), fil, now, manila))
        assertEquals("Yesterday, 11:50 PM", reportedAtLabel(at(2026, 9, 11, 23, 50), en, now, manila))
    }

    @Test
    fun `an older report names the month`() {
        assertEquals("Setyembre 10, 4:15 PM", reportedAtLabel(at(2026, 9, 10, 16, 15), fil, now, manila))
        assertEquals("September 10, 4:15 PM", reportedAtLabel(at(2026, 9, 10, 16, 15), en, now, manila))
    }

    @Test
    fun `a report from another year includes the year`() {
        assertEquals("Disyembre 30, 2025, 9:05 AM", reportedAtLabel(at(2025, 12, 30, 9, 5), fil, now, manila))
    }

    @Test
    fun `midnight and noon read as twelve, not zero`() {
        assertEquals("12:00 AM", reportedTimeLabel(at(2026, 9, 12, 0, 0), manila))
        assertEquals("12:30 PM", reportedTimeLabel(at(2026, 9, 12, 12, 30), manila))
    }

    @Test
    fun `age reads in whole words`() {
        assertEquals("Ngayon lang", ageLabel(20_000, fil))
        assertEquals("7 minuto na ang nakalipas", ageLabel(7 * 60_000L, fil))
        assertEquals("2 oras na ang nakalipas", ageLabel(120 * 60_000L, fil))
        assertEquals("1 oras at 5 minuto na ang nakalipas", ageLabel(65 * 60_000L, fil))
        assertEquals("14 araw na ang nakalipas", ageLabel(14 * 24 * 60 * 60_000L, fil))
        assertEquals("7 min ago", ageLabel(7 * 60_000L, en))
        assertEquals("1 day ago", ageLabel(26 * 60 * 60_000L, en))
    }

    @Test
    fun `samples are labelled as samples, never as a neighbour's report`() {
        assertEquals(OriginText("Halimbawang ulat", "Kasama sa app, pang-demo"), originText("seed", 0, fil))
        assertEquals("Sample report", originText("seed", 0, en).main)
        assertEquals(OriginText("Mula sa phone mo", "Ikaw ang nag-ulat nito"), originText("local", 0, fil))
    }

    @Test
    fun `a relayed report says how many phones it passed through`() {
        assertEquals(OriginText("Ipinasa ng kalapit na phone", "Dumaan sa 2 phone"), originText("mesh", 2, fil))
        assertEquals("Through 1 phone", originText("mesh", 1, en).helper)
        assertNull(originText("mesh", 0, fil).helper)
    }

    @Test
    fun `confidence buckets in plain words`() {
        assertEquals("Hindi pa kumpirmado", bucketLabel("unverified", fil))
        assertEquals("Malamang totoo", bucketLabel("likely", fil))
        assertEquals("Kumpirmado", bucketLabel("confirmed", fil))
        assertEquals("Opisyal na ulat", bucketLabel("official", fil))
        assertEquals("Likely true", bucketLabel("likely", en))
    }
}
