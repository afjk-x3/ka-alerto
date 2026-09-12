package com.macci.kaalerto.sos

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosQrTest {

    private val manila = ZoneId.of("Asia/Manila")
    private val at1240 = LocalDateTime.of(2026, 9, 12, 12, 40).atZone(manila).toInstant().toEpochMilli()

    private val full = RescueCardInfo(
        name = "Juan D.",
        homeBarangay = "San Juan Bautista",
        lat = 18.1709012,
        lon = 120.6058049,
        accuracyMeters = 5.4f,
        people = PeopleCount.FEW,
        createdAtMs = at1240,
    )

    @Test
    fun `the payload is plain text a rescuer can read in any camera app`() {
        assertEquals(
            listOf(
                "KAILANGAN NG SAGIP / RESCUE NEEDED",
                "Juan D., taga-San Juan Bautista",
                "18.17090, 120.60580 (+/-5 m)",
                "Ilan: 2-4",
                "Oras: 2026-09-12 12:40",
                "geo:18.17090,120.60580",
            ).joinToString("\n"),
            rescueCardPayload(full, manila),
        )
    }

    @Test
    fun `with no fix it says so and carries no geo line`() {
        val payload = rescueCardPayload(full.copy(lat = null, lon = null, accuracyMeters = null), manila)
        assertTrue(payload.contains("Lokasyon: hindi makuha"))
        assertFalse(payload.contains("geo:"))
    }

    @Test
    fun `before registration there is no name line, and nothing is invented`() {
        val payload = rescueCardPayload(full.copy(name = null, homeBarangay = null, people = null), manila)
        assertEquals("KAILANGAN NG SAGIP / RESCUE NEEDED", payload.lines().first())
        assertTrue(payload.lines()[1].startsWith("18.17090"))
        assertFalse(payload.contains("Ilan:"))
    }

    @Test
    fun `coordinates never use a decimal comma`() {
        assertEquals("18.17090", coord(18.1709012))
        assertEquals("-0.50000", coord(-0.5))
    }

    /** The real check: what the card draws decodes back to exactly what it meant to say. */
    @Test
    fun `the QR decodes back to the payload, including non-ASCII names`() {
        listOf(full, full.copy(name = "Niño S.", homeBarangay = "Dumalneg")).forEach { info ->
            val payload = rescueCardPayload(info, manila)
            assertEquals(payload, decode(encodeQr(payload)))
        }
    }

    private fun decode(matrix: QrMatrix): String {
        val scale = 4
        val quiet = 4
        val side = (matrix.size + quiet * 2) * scale
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0xFF000000.toInt()
                    }
                }
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }
}
