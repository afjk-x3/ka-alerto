package com.macci.kaalerto.sos

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUILD_TASKS.md day 8's DoD says the rescue card shows a **scannable** QR — and
 * design/README.md's open-items list has long carried "the rescue-card QR is a drawn
 * placeholder, not a scannable code".
 *
 * So these tests do not check that a QR was drawn. They render the matrix to pixels the
 * way the card does, hand those pixels to ZXing's *decoder*, and assert the payload
 * comes back. That is as close to pointing a camera at the screen as a unit test can
 * get, and it is what turns "scannable" from a claim into a checked fact.
 */
class SosQrTest {

    private val snapshot = SosSnapshot(
        sosId = "sos-9f2a4c1e-0b77-4d31-9a55-2e6f8c0d1234",
        startedAtMs = 1_700_000_000_000L,
        lat = 18.171234,
        lon = 120.593456,
        accuracyMeters = 6f,
        state = SosState.UNREACHABLE,
        context = SosContext(
            people = "5–8",
            companions = listOf("Bata", "Matanda"),
            medical = listOf("Gamot sa puso"),
            water = "Dibdib",
            trend = "Tumataas",
        ),
        authorName = "Residente A1B2",
        isMine = true,
    )

    /** Renders the matrix exactly as [QrCode] does: whole-pixel modules, 4-module quiet zone. */
    private fun render(matrix: QrMatrix, modulePx: Int = 4): Triple<IntArray, Int, Int> {
        val quietZone = 4
        val side = (matrix.size + quietZone * 2) * modulePx
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                val originX = (x + quietZone) * modulePx
                val originY = (y + quietZone) * modulePx
                for (dy in 0 until modulePx) {
                    for (dx in 0 until modulePx) {
                        pixels[(originY + dy) * side + originX + dx] = 0xFF000000.toInt()
                    }
                }
            }
        }
        return Triple(pixels, side, side)
    }

    private fun decode(content: String): String {
        val (pixels, width, height) = render(encodeQr(content))
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        val result = QRCodeReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true))
        return result.text
    }

    @Test
    fun `a full rescue card round-trips through an actual QR decode`() {
        val encoded = snapshot.toCard().encode()

        val scanned = decode(encoded)

        assertEquals(encoded, scanned)
        val card = decodeSosCard(scanned)
        assertNotNull(card)
        assertEquals(snapshot.sosId, card!!.sosId)
        assertEquals(snapshot.lat, card.lat, 0.000001)
        assertEquals(snapshot.lon, card.lon, 0.000001)
        assertEquals("Gamot sa puso", card.medical.single())
        assertEquals("Dibdib", card.water)
    }

    @Test
    fun `a request with no context at all still produces a scannable code`() {
        // The realistic worst case is not the biggest payload — it is someone who
        // pressed and held and then dropped the phone. That card must still scan.
        val bare = snapshot.copy(context = SosContext(), accuracyMeters = null).toCard().encode()

        assertEquals(bare, decode(bare))
    }

    @Test
    fun `the payload carries the request itself, not a link to it`() {
        val encoded = snapshot.toCard().encode()

        // docs/03-architecture.md §6.4.3: whoever scans this has no network either, so a
        // URL would be useless to exactly the person it is meant for.
        assertTrue(encoded.startsWith(SOS_QR_PREFIX))
        assertTrue(!encoded.contains("http"))
        assertTrue(encoded.contains("18.171234"))
    }

    @Test
    fun `a QR that is not ours is rejected rather than half-parsed`() {
        assertNull(decodeSosCard("https://example.com"))
        assertNull(decodeSosCard("""{"id":"sos-1","la":1.0}"""))
        assertNull(decodeSosCard(SOS_QR_PREFIX + "not json"))
    }

    @Test
    fun `the code stays small enough to read off a phone screen`() {
        val matrix = encodeQr(snapshot.toCard().encode())

        // A 150dp card region with a 4-module quiet zone leaves roughly 2.5 px per
        // module at 3x density by version 41 (177 modules) — too fine for a camera
        // pointed at a wet screen. Version 25 is 117 modules, which is comfortable.
        assertTrue("QR grew to ${matrix.size} modules", matrix.size <= 117)
    }
}
