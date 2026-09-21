package com.macci.kaalerto.geofence

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldPromptConfirmTest {

    private fun event(id: String, author: String, type: String = "flood_report", ref: String? = "w1ab") = Event(
        id = id, type = type, lat = 18.17, lon = 120.6, featureRef = ref, severity = "S3", waterLevel = null,
        authorId = author, authorName = author, authorRole = "resident", timestampMs = 0, expiresAt = 1,
        origin = "mesh", hopCount = 1, note = null,
    )

    @Test
    fun `someone else's fresh report prompts`() {
        val report = event("r1", "other")
        assertTrue(shouldPromptConfirm(report, listOf(report), "me"))
    }

    @Test
    fun `my own report does not prompt me`() {
        val report = event("r1", "me")
        assertFalse(shouldPromptConfirm(report, listOf(report), "me"))
    }

    @Test
    fun `a feature I already confirmed or disputed does not prompt`() {
        val report = event("r2", "other")
        val mine = event("c1", "me", type = "confirm")
        assertFalse(shouldPromptConfirm(report, listOf(report, mine), "me"))
    }

    @Test
    fun `my say on a different feature does not suppress the prompt`() {
        val report = event("r2", "other")
        val mine = event("c1", "me", type = "confirm", ref = "zzzz")
        assertTrue(shouldPromptConfirm(report, listOf(report, mine), "me"))
    }

    @Test
    fun `a report with no feature ref cannot be opened, so it does not prompt`() {
        val report = event("r3", "other", ref = null)
        assertFalse(shouldPromptConfirm(report, listOf(report), "me"))
    }
}
