package com.macci.kaalerto.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pure wire-format logic for LAN auto-discovery of the sync server — mirrors
 * `ServerSync.kt`'s pure/impure split. The actual UDP socket I/O lives in
 * `ServerDiscoveryClient.kt`; this file only builds the request and decodes a reply, so
 * both are unit-testable with no network.
 *
 * [DISCOVERY_PORT] and both magic strings must match `server/src/discovery.js` exactly
 * — there is no negotiation, just an agreed convention on both ends, the same
 * relationship [MAX_PUSH_CHUNK_SIZE] already has with the server's own `MAX_BATCH_SIZE`.
 */
const val DISCOVERY_PORT = 41889
private const val DISCOVERY_REQUEST_MAGIC = "KAALERTO_DISCOVER_V1"
private const val DISCOVERY_RESPONSE_MAGIC = "KAALERTO_SERVER_V1"

fun discoveryRequestBytes(): ByteArray = DISCOVERY_REQUEST_MAGIC.toByteArray(Charsets.UTF_8)

private val discoveryJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class DiscoveryReplyWire(val magic: String, val httpPort: Int)

/**
 * Returns the server's HTTP port if [bytes] (of which only the first [length] are the
 * actual packet — a [java.net.DatagramPacket] buffer is reused and typically longer than
 * the datagram it received) is a genuine reply from this app's own server, or null for
 * anything else: a malformed packet, the wrong magic string, or a stray broadcast from
 * something unrelated on the same LAN. Same fail-closed convention as
 * `ServerSync.decodePullResponse`.
 *
 * The sender's IP is deliberately not decoded here — it is never part of the JSON body,
 * only of the received [java.net.DatagramPacket] itself, which only the impure
 * `ServerDiscoveryClient` ever sees. [buildDiscoveredBaseUrl] combines the two.
 */
fun decodeDiscoveryReplyPort(bytes: ByteArray, length: Int): Int? =
    runCatching {
        discoveryJson.decodeFromString(DiscoveryReplyWire.serializer(), String(bytes, 0, length, Charsets.UTF_8))
    }.getOrNull()
        ?.takeIf { it.magic == DISCOVERY_RESPONSE_MAGIC }
        ?.httpPort
        ?.takeIf { it in 1..65535 }

/** Same "no scheme, host:port typed by a person" shape [normalizeBaseUrl] accepts as
 * input — a discovered address is meant to land in the same draft field and go through
 * the same save path as one someone typed by hand. */
fun buildDiscoveredBaseUrl(hostAddress: String, port: Int): String = "http://$hostAddress:$port"
