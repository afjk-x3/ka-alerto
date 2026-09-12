'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const dgram = require('node:dgram');
const { startDiscoveryResponder, DISCOVERY_PORT, REQUEST_MAGIC, RESPONSE_MAGIC } = require('../src/discovery');

// Sent to 127.0.0.1 rather than a real broadcast address: this test verifies the
// responder's own request/reply logic, not LAN broadcast propagation, which cannot be
// exercised in a sandboxed test run and is a manual-verification concern the same way
// every other transport in this project is (see CLAUDE.md's mesh/server-sync passes).
test('discovery responder replies to a well-formed request with the http port', async () => {
  const responder = startDiscoveryResponder(4242);
  const client = dgram.createSocket('udp4');
  try {
    const reply = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('timed out waiting for discovery reply')), 2000);
      client.on('message', (msg) => {
        clearTimeout(timer);
        resolve(JSON.parse(msg.toString('utf8')));
      });
      client.on('error', reject);
      client.bind(0, () => {
        client.send(Buffer.from(REQUEST_MAGIC, 'utf8'), DISCOVERY_PORT, '127.0.0.1');
      });
    });
    assert.equal(reply.magic, RESPONSE_MAGIC);
    assert.equal(reply.httpPort, 4242);
  } finally {
    client.close();
    responder.close();
  }
});

test('discovery responder ignores a packet that is not the expected magic string', async () => {
  const responder = startDiscoveryResponder(4242);
  const client = dgram.createSocket('udp4');
  try {
    let gotUnexpectedReply = false;
    client.on('message', () => {
      gotUnexpectedReply = true;
    });
    await new Promise((resolve) => client.bind(0, resolve));
    client.send(Buffer.from('not the magic string', 'utf8'), DISCOVERY_PORT, '127.0.0.1');
    await new Promise((resolve) => setTimeout(resolve, 300));
    assert.equal(gotUnexpectedReply, false);
  } finally {
    client.close();
    responder.close();
  }
});
