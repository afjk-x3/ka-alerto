package com.macci.kaalerto.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * The impure half of LAN auto-discovery — see `ServerDiscovery.kt` for the wire format
 * and `server/src/discovery.js` for the responder this talks to. Broadcasts one discovery
 * request to the LAN's limited-broadcast address (255.255.255.255) and waits up to
 * [timeoutMs] for the first genuine reply.
 *
 * No new Android permission needed: `INTERNET`/`ACCESS_NETWORK_STATE` are already
 * declared for `ServerSyncLoop`'s own HTTP traffic, and a [DatagramSocket] broadcast
 * needs neither a runtime nor an extra install-time permission on top of those.
 *
 * Fails closed (returns null) on any timeout, malformed reply, or socket error — the
 * caller (`identity/ProfileFields.kt`'s "Hanapin" control, via `ui/KaAlertoApp.kt`)
 * treats null exactly like "nothing found," never as an error state, because the manual
 * field is always the fallback: this is a convenience, not a transport anything else
 * depends on.
 */
object ServerDiscoveryClient {
    private const val DEFAULT_TIMEOUT_MS = 2_000
    private const val REPLY_BUFFER_SIZE = 512

    suspend fun discover(timeoutMs: Int = DEFAULT_TIMEOUT_MS): String? = withContext(Dispatchers.IO) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs

                val requestBytes = discoveryRequestBytes()
                socket.send(
                    DatagramPacket(
                        requestBytes,
                        requestBytes.size,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_PORT,
                    ),
                )

                val buffer = ByteArray(REPLY_BUFFER_SIZE)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                val port = decodeDiscoveryReplyPort(buffer, response.length) ?: return@use null
                val host = response.address?.hostAddress ?: return@use null
                buildDiscoveredBaseUrl(host, port)
            }
        }.getOrNull()
    }
}
