'use strict';

const dgram = require('node:dgram');

/**
 * LAN auto-discovery for this server's address — see
 * `android/.../identity/ProfileFields.kt`'s `ServerUrlField` "Hanapin" control, the only
 * caller. Built on `node:dgram` (stdlib) rather than mDNS/Bonjour: CLAUDE.md is explicit
 * that Express is the only npm dependency this server takes on, and a plain request/reply
 * UDP exchange needs no library on either end.
 *
 * This only replies to a request it actually receives — it never broadcasts on its own
 * timer — so an idle server with nobody looking for it produces zero discovery traffic.
 * The reply carries only the HTTP port; the requester's own OS already knows this
 * server's IP from the reply packet's source address, so there is nothing else to send.
 *
 * DISCOVERY_PORT and both magic strings must match
 * `android/.../sync/ServerDiscovery.kt` exactly — there is no negotiation, just an agreed
 * convention on both ends, the same relationship `MAX_PUSH_CHUNK_SIZE` already has with
 * this server's own `MAX_BATCH_SIZE`.
 */
const DISCOVERY_PORT = 41889;
const REQUEST_MAGIC = 'KAALERTO_DISCOVER_V1';
const RESPONSE_MAGIC = 'KAALERTO_SERVER_V1';

/**
 * Starts the responder and returns the bound socket so the caller can close it on
 * shutdown, same lifecycle as `server.js`'s own `http.Server`. A malformed or unrelated
 * packet (something else on the LAN, or a stray retransmit) is silently ignored rather
 * than replied to or logged as an error — this is a best-effort convenience, not a
 * channel anything else in the app depends on.
 */
function startDiscoveryResponder(httpPort) {
  const socket = dgram.createSocket('udp4');

  socket.on('message', (msg, rinfo) => {
    if (msg.toString('utf8') !== REQUEST_MAGIC) return;
    const reply = Buffer.from(JSON.stringify({ magic: RESPONSE_MAGIC, httpPort }), 'utf8');
    socket.send(reply, rinfo.port, rinfo.address);
  });

  // Non-fatal by design: a discovery hiccup (e.g. the port already in use on a machine
  // running a second instance) must never take down the HTTP server it starts alongside
  // — this transport is exactly as optional as every other one in this app.
  socket.on('error', (err) => {
    console.error('discovery responder error (non-fatal, HTTP server unaffected):', err.message);
  });

  socket.bind(DISCOVERY_PORT);

  return socket;
}

module.exports = { startDiscoveryResponder, DISCOVERY_PORT, REQUEST_MAGIC, RESPONSE_MAGIC };
