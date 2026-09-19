package com.macci.kaalerto.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTipTest {
    @Test
    fun `xiaomi, redmi and poco phones get the tip, whatever the capitalisation`() {
        assertTrue(BackgroundTip.isXiaomiFamily("Xiaomi"))
        assertTrue(BackgroundTip.isXiaomiFamily("xiaomi"))
        assertTrue(BackgroundTip.isXiaomiFamily("Redmi"))
        assertTrue(BackgroundTip.isXiaomiFamily(" POCO "))
    }

    @Test
    fun `other makers do not, because the wording is specific to MIUI and HyperOS`() {
        assertFalse(BackgroundTip.isXiaomiFamily("samsung"))
        assertFalse(BackgroundTip.isXiaomiFamily("Google"))
        assertFalse(BackgroundTip.isXiaomiFamily(""))
    }
}
