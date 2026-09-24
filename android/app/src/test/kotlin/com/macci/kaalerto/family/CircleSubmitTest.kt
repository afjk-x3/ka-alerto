package com.macci.kaalerto.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleSubmitTest {

    private val id = "circle-0f8a3c2e-1b4d-4a9e-9c3f-2a7b6d5e4c1a"

    @Test
    fun `bare circleId returns itself`() {
        assertEquals(id, extractCircleId(id))
    }

    @Test
    fun `full share sentence returns just the id`() {
        val sentence = "Sumali sa aming Circle sa KaAlerto: $id"
        assertEquals(id, extractCircleId(sentence))
    }

    @Test
    fun `unrelated text returns null`() {
        assertNull(extractCircleId("hey are you safe? call me when you can"))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(extractCircleId(""))
    }

    @Test
    fun `short code is eight digits and stable for one circle`() {
        val code = shortCircleCode(id)
        assertTrue(Regex("""\d{4}-\d{4}""").matches(code))
        assertEquals(code, shortCircleCode(id))
    }

    @Test
    fun `a short code finds a circle this phone knows, however it is typed`() {
        val create = newCircleCreateEvent(
            com.macci.kaalerto.identity.LocalIdentity.Identity("local-a", "Ana R.", "resident"),
            id, "Bahay", 1_700_000_000_000L,
        )
        val code = shortCircleCode(id)
        assertEquals(id, resolveJoinCode(code, listOf(create)))
        assertEquals(id, resolveJoinCode(code.replace("-", ""), listOf(create)))
        assertEquals(id, resolveJoinCode("Code: ${code.replace("-", " ")}", listOf(create)))
        assertNull(resolveJoinCode(code, emptyList()))
        assertEquals(id, resolveJoinCode(id, emptyList()))
    }
}
