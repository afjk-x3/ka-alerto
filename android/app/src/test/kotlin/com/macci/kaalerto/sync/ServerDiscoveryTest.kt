package com.macci.kaalerto.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerDiscoveryTest {

    private fun replyBytes(json: String) = json.toByteArray(Charsets.UTF_8)

    @Test
    fun `decodes a genuine reply and returns the http port`() {
        val bytes = replyBytes("""{"magic":"KAALERTO_SERVER_V1","httpPort":3000}""")
        assertEquals(3000, decodeDiscoveryReplyPort(bytes, bytes.size))
    }

    @Test
    fun `rejects a reply with the wrong magic string`() {
        val bytes = replyBytes("""{"magic":"SOMETHING_ELSE","httpPort":3000}""")
        assertNull(decodeDiscoveryReplyPort(bytes, bytes.size))
    }

    @Test
    fun `rejects malformed json`() {
        val bytes = replyBytes("not json at all")
        assertNull(decodeDiscoveryReplyPort(bytes, bytes.size))
    }

    @Test
    fun `rejects a port outside the valid range`() {
        val bytes = replyBytes("""{"magic":"KAALERTO_SERVER_V1","httpPort":0}""")
        assertNull(decodeDiscoveryReplyPort(bytes, bytes.size))

        val negative = replyBytes("""{"magic":"KAALERTO_SERVER_V1","httpPort":-1}""")
        assertNull(decodeDiscoveryReplyPort(negative, negative.size))
    }

    @Test
    fun `only reads the first 'length' bytes, ignoring trailing buffer garbage`() {
        // Mirrors a real DatagramPacket buffer: reused and typically longer than the
        // datagram actually received, with stale bytes from a previous receive beyond
        // the real payload's length.
        val real = replyBytes("""{"magic":"KAALERTO_SERVER_V1","httpPort":8080}""")
        val padded = real + ByteArray(50) { 'x'.code.toByte() }
        assertEquals(8080, decodeDiscoveryReplyPort(padded, real.size))
    }

    @Test
    fun `request bytes decode back to the agreed magic string`() {
        assertEquals("KAALERTO_DISCOVER_V1", String(discoveryRequestBytes(), Charsets.UTF_8))
    }

    @Test
    fun `builds a base url from a discovered host and port`() {
        assertEquals("http://192.168.1.42:3000", buildDiscoveredBaseUrl("192.168.1.42", 3000))
    }
}
