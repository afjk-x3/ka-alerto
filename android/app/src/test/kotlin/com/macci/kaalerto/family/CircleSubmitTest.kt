package com.macci.kaalerto.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
