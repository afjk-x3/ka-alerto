package com.macci.kaalerto.family

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.macci.kaalerto.sos.QrMatrix
import com.macci.kaalerto.sos.encodeQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors `sos/SosQrTest.kt`'s approach exactly: render the matrix to real pixels and
 * hand them to ZXing's *decoder*, so "scannable" is a checked fact, not a claim. */
class CircleQrTest {

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

    private val card = CircleCard(authorId = "local-a1b2c3d4", authorName = "Residente A1B2")

    @Test
    fun `a circle card round-trips through an actual QR decode`() {
        val encoded = card.encode()

        val scanned = decode(encoded)

        assertEquals(encoded, scanned)
        val decoded = decodeCircleCard(scanned)
        assertNotNull(decoded)
        assertEquals(card.authorId, decoded!!.authorId)
        assertEquals(card.authorName, decoded.authorName)
    }

    @Test
    fun `the payload carries the identity itself, not a link to it`() {
        val encoded = card.encode()

        assertTrue(encoded.startsWith(CIRCLE_QR_PREFIX))
        assertTrue(!encoded.contains("http"))
        assertTrue(encoded.contains(card.authorId))
    }

    @Test
    fun `a QR that is not ours is rejected rather than half-parsed`() {
        assertNull(decodeCircleCard("https://example.com"))
        assertNull(decodeCircleCard("""{"authorId":"local-1"}"""))
        assertNull(decodeCircleCard(CIRCLE_QR_PREFIX + "not json"))
        // Also not confusable with the SOS card's own prefix:
        assertNull(decodeCircleCard("KAALERTO/SOS/1:{}"))
    }

    @Test
    fun `the code stays coarse enough to read off a phone screen`() {
        val matrix = encodeQr(card.encode())

        // Same worst-case-device math as SosQrTest — a 150dp box at 2.0x density.
        val worstCaseBoxPx = 300
        val modulePx = worstCaseBoxPx / (matrix.size + 8)
        assertTrue("QR is ${matrix.size} modules -> ${modulePx}px per module", modulePx >= 3)
    }
}
