// The PIN gate every API route shares (src/lib/dashboardAuth.ts). Run: npm run check
import assert from 'node:assert/strict';
import { checkDashboardPin } from '../src/lib/dashboardAuth.ts';

const PIN = '1234';
const req = (pin: string | undefined, ip: string) =>
  new Request('http://localhost/api/events', {
    headers: { ...(pin !== undefined ? { 'x-dashboard-pin': pin } : {}), 'x-forwarded-for': ip },
  });

async function status(res: Response | null): Promise<{ status: number | null; error?: string }> {
  if (res === null) return { status: null };
  const body = (await res.json().catch(() => ({}))) as { error?: string };
  return { status: res.status, error: body.error };
}

let failed = 0;
let passed = 0;
function test(name: string, fn: () => void | Promise<void>) {
  return (async () => {
    try {
      await fn();
      passed++;
    } catch (e) {
      failed++;
      console.error(`FAIL  ${name}\n      ${(e as Error).message.split('\n')[0]}`);
    }
  })();
}

// Each test picks its own IP so the module-level attempt counter never leaks between them.
let nextIp = 0;
const freshIp = () => `10.0.0.${++nextIp}`;

await test('no DASHBOARD_PIN, a read route: lets everything through', async () => {
  delete process.env.DASHBOARD_PIN;
  assert.equal(checkDashboardPin(req(undefined, freshIp())), null);
});

await test('no DASHBOARD_PIN, a write route: refused, not silently open', async () => {
  delete process.env.DASHBOARD_PIN;
  const { status: s, error } = await status(checkDashboardPin(req('anything', freshIp()), { requirePin: true }));
  assert.equal(s, 403);
  assert.match(error ?? '', /DASHBOARD_PIN/);
});

await test('the right PIN gets through', async () => {
  process.env.DASHBOARD_PIN = PIN;
  assert.equal(checkDashboardPin(req(PIN, freshIp())), null);
});

await test('a wrong PIN, same length as the real one, is still rejected', async () => {
  process.env.DASHBOARD_PIN = PIN;
  const { status: s, error } = await status(checkDashboardPin(req('5678', freshIp())));
  assert.equal(s, 401);
  assert.equal(error, 'unauthorized');
});

await test('no PIN header at all is treated as wrong, not skipped', async () => {
  process.env.DASHBOARD_PIN = PIN;
  const { status: s } = await status(checkDashboardPin(req(undefined, freshIp())));
  assert.equal(s, 401);
});

await test('the 6th wrong guess in a row is locked out, not just refused', async () => {
  process.env.DASHBOARD_PIN = PIN;
  const ip = freshIp();
  const now = 1_700_000_000_000;
  for (let i = 0; i < 5; i++) {
    const { status: s } = await status(checkDashboardPin(req('wrong', ip), { now: now + i }));
    assert.equal(s, 401, `attempt ${i + 1} should still be a plain 401`);
  }
  const { status: s, error } = await status(checkDashboardPin(req('wrong', ip), { now: now + 5 }));
  assert.equal(s, 429);
  assert.match(error ?? '', /Try again in \d+s/);
});

await test('the real PIN is refused too while locked out', async () => {
  process.env.DASHBOARD_PIN = PIN;
  const ip = freshIp();
  const now = 1_700_000_000_000;
  for (let i = 0; i < 6; i++) checkDashboardPin(req('wrong', ip), { now: now + i });
  const { status: s } = await status(checkDashboardPin(req(PIN, ip), { now: now + 6 }));
  assert.equal(s, 429);
});

await test('once the lockout window passes, the real PIN works again', async () => {
  process.env.DASHBOARD_PIN = PIN;
  const ip = freshIp();
  const now = 1_700_000_000_000;
  for (let i = 0; i < 6; i++) checkDashboardPin(req('wrong', ip), { now: now + i });
  // 30s lockout: one second later still locked, 31s later clear.
  assert.equal((await status(checkDashboardPin(req(PIN, ip), { now: now + 29_000 }))).status, 429);
  assert.equal(checkDashboardPin(req(PIN, ip), { now: now + 31_000 }), null);
});

await test('a single wrong guess right after a lockout expires does not immediately re-lock the next one', async () => {
  process.env.DASHBOARD_PIN = PIN;
  const ip = freshIp();
  const now = 1_700_000_000_000;
  for (let i = 0; i < 6; i++) checkDashboardPin(req('wrong', ip), { now: now + i }); // locked from here on
  // The lockout itself resets the count to 0, so one miss right after it expires is attempt 1 of a
  // fresh 5, not attempt 6 of the old run — the request straight after that first miss must still be
  // a plain 401, not another lockout already. (Both individual responses here are always 401 by
  // themselves — see the function's own comment on why — so the state has to be read through a
  // second call, not the first one's status code.)
  await status(checkDashboardPin(req('wrong', ip), { now: now + 31_000 }));
  const { status: s } = await status(checkDashboardPin(req('wrong', ip), { now: now + 31_001 }));
  assert.equal(s, 401);
});

await test('a correct PIN resets the count, so the next miss is not the "last straw"', async () => {
  process.env.DASHBOARD_PIN = PIN;
  const ip = freshIp();
  const now = 1_700_000_000_000;
  for (let i = 0; i < 4; i++) checkDashboardPin(req('wrong', ip), { now: now + i }); // 4 misses, one short of locking
  assert.equal(checkDashboardPin(req(PIN, ip), { now: now + 4 }), null); // success
  const { status: s } = await status(checkDashboardPin(req('wrong', ip), { now: now + 5 })); // 1 fresh miss
  assert.equal(s, 401);
});

await test('two IPs are locked out independently', async () => {
  process.env.DASHBOARD_PIN = PIN;
  const a = freshIp();
  const b = freshIp();
  const now = 1_700_000_000_000;
  for (let i = 0; i < 6; i++) checkDashboardPin(req('wrong', a), { now: now + i });
  const { status: s } = await status(checkDashboardPin(req('wrong', b), { now: now + 6 }));
  assert.equal(s, 401, "b's own first miss, unaffected by a's lockout");
});

console.log(`${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
