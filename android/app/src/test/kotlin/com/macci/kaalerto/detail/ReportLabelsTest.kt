package com.macci.kaalerto.detail

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportLabelsTest {

    private val manila = ZoneId.of("Asia/Manila")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(manila).toInstant().toEpochMilli()

    private val now = at(2026, 9, 12, 10, 45)

    @Test
    fun `a report from earlier today says today`() {
        assertEquals("Ngayong araw, 10:36 AM", reportedAtLabel(at(2026, 9, 12, 10, 36), now, manila))
    }

    /** Calendar day, not "within 24 hours" — 11:50 PM last night is yesterday, even 11 hours on. */
    @Test
    fun `a report from the previous calendar day says yesterday`() {
        assertEquals("Kahapon, 11:50 PM", reportedAtLabel(at(2026, 9, 11, 23, 50), now, manila))
    }

    @Test
    fun `an older report names the month in Filipino`() {
        assertEquals("Setyembre 10, 4:15 PM", reportedAtLabel(at(2026, 9, 10, 16, 15), now, manila))
    }

    @Test
    fun `a report from another year includes the year`() {
        assertEquals("Disyembre 30, 2025, 9:05 AM", reportedAtLabel(at(2025, 12, 30, 9, 5), now, manila))
    }

    @Test
    fun `midnight and noon read as twelve, not zero`() {
        assertEquals("Ngayong araw, 12:00 AM", reportedAtLabel(at(2026, 9, 12, 0, 0), now, manila))
        assertEquals("Kahapon, 12:00 PM", reportedAtLabel(at(2026, 9, 11, 12, 0), now, manila))
    }

    @Test
    fun `day and time are available separately for narrow columns`() {
        val ts = at(2026, 9, 11, 7, 3)
        assertEquals("Kahapon", reportedDayLabel(ts, now, manila))
        assertEquals("7:03 AM", reportedTimeLabel(ts, manila))
    }

    @Test
    fun `seed reports are labelled honestly as samples`() {
        assertEquals(OriginText("Halimbawang ulat", "Kasama sa app, pang-demo"), originText("seed", hopCount = 0))
    }

    @Test
    fun `a report filed on this phone says so`() {
        assertEquals(OriginText("Mula sa phone mo", "Ikaw ang nag-ulat nito"), originText("local", hopCount = 0))
    }

    @Test
    fun `a relayed report counts phones, not hops`() {
        assertEquals(OriginText("Ipinasa ng kalapit na phone", "Dumaan sa 2 phone"), originText("mesh", hopCount = 2))
    }

    @Test
    fun `a relayed report with no hop count has no helper line`() {
        assertNull(originText("mesh", hopCount = 0).helper)
    }

    @Test
    fun `sms and server origins read as text and internet`() {
        assertEquals("Galing sa text", originText("sms", hopCount = 0).main)
        assertEquals("Galing sa internet", originText("server", hopCount = 0).main)
    }

    @Test
    fun `an unrecognised origin is not passed off as this phone`() {
        assertEquals(OriginText("Hindi alam kung saan galing", null), originText("carrier-pigeon", hopCount = 0))
    }

    @Test
    fun `age reads in whole Filipino words, not "min ago"`() {
        assertEquals("Ngayon lang", ageLabel(30_000))
        assertEquals("1 minuto na ang nakalipas", ageLabel(60_000))
        assertEquals("7 minuto na ang nakalipas", ageLabel(7 * 60_000L))
        assertEquals("1 oras na ang nakalipas", ageLabel(60 * 60_000L))
        assertEquals("2 oras at 5 minuto na ang nakalipas", ageLabel(125 * 60_000L))
        assertEquals("3 araw na ang nakalipas", ageLabel((3 * 24 * 60 + 5) * 60_000L))
    }

    @Test
    fun `confidence buckets are in Filipino`() {
        assertEquals("Hindi pa kumpirmado", bucketLabel("unverified"))
        assertEquals("Malamang totoo", bucketLabel("likely"))
        assertEquals("Kumpirmado", bucketLabel("confirmed"))
        assertEquals("Opisyal na ulat", bucketLabel("official"))
    }

    @Test
    fun `roles are in Filipino, and an unknown role reads as a resident`() {
        assertEquals("Opisyal ng barangay", roleLabel("official"))
        assertEquals("Tagasagip", roleLabel("responder"))
        assertEquals("Residente", roleLabel("resident"))
        assertEquals("Residente", roleLabel("something-new"))
    }

    /** Same words as the two buttons a resident just saw, so the count explains itself. */
    @Test
    fun `the agreement count uses the button words`() {
        assertEquals("1 nagsabing tama · 0 nagsabing iba na", agreementLabel(confirms = 1, disputes = 0))
    }

    @Test
    fun `a dispute reason reads as the option the resident tapped`() {
        assertEquals("Humupa na", disputeReasonLabel("cleared_now"))
        assertEquals("Lumala", disputeReasonLabel("worse"))
        assertEquals("Bumaba", disputeReasonLabel("shallower"))
        assertEquals("Maling lokasyon", disputeReasonLabel("wrong_location"))
        assertEquals("Iba na", disputeReasonLabel(null))
    }
}
