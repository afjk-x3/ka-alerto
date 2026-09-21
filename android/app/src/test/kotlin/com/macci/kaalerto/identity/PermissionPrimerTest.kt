package com.macci.kaalerto.identity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPrimerTest {

    @Test
    fun `the map explains permissions only when something is missing`() {
        assertTrue(shouldShowPrimer(listOf(Perm.LOCATION), alreadyAnswered = false))
        assertFalse(shouldShowPrimer(emptyList(), alreadyAnswered = false))
    }

    @Test
    fun `once answered, either way, it never asks again on its own`() {
        assertFalse(shouldShowPrimer(listOf(Perm.LOCATION, Perm.NEARBY), alreadyAnswered = true))
    }

    @Test
    fun `the camera is not part of the map's explanation, since only the QR scanner uses it`() {
        assertFalse(Perm.CAMERA in PRIMER_PERMS)
        assertTrue(PRIMER_PERMS.containsAll(listOf(Perm.LOCATION, Perm.NOTIFICATIONS, Perm.NEARBY)))
    }
}
