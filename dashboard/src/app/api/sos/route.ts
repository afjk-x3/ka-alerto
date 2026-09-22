// Writes an `sos_state` event (the operator acknowledging a request) to Supabase — added 22 Sep
// 2026 (review D3: the dashboard was read-only). This rides the exact mechanic a phone's own "Nakita
// ko" button already uses (android/.../sos/SosViewModel.kt's `advance`): the same event type, the same
// payload shape, folded by the same rule (dashboard/.../lib/items.ts's STATE_ORDER) a phone folds it by.
// `sos_state` is never redacted on egress (android/.../sos/SosMeshPolicy.kt's `redactSosOnEgress`) — it
// carries the *responder's* name on purpose, so "Dashboard" showing up here is exactly the point, the
// same way a named volunteer's name shows up when they acknowledge from their own phone.
//
// Deliberately narrow, same trust model as /api/evac: one shared PIN, no roster seat, refused outright
// when DASHBOARD_PIN is unset. "Assign" and "note" are not built — neither has a wire format on either
// side, and inventing one is real new scope beyond closing the read-only gap.
import { randomUUID } from 'node:crypto';
import { checkDashboardPin } from '@/lib/dashboardAuth';

// Mirrors android/.../sos/SosEvents.kt's SOS_TTL_MS — a follow-up shares the request's own lifetime
// rather than starting a fresh one, so an ack posted 11 hours in doesn't outlive the request it's on.
const SOS_TTL_MS = 12 * 60 * 60 * 1000;

const bad = (message: string) => Response.json({ error: message }, { status: 400 });
const text = (v: unknown, max: number): string | null => (typeof v === 'string' && v.trim() && v.length <= max ? v.trim() : null);
const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : null);

export async function POST(request: Request) {
  const authFail = checkDashboardPin(request, { requirePin: true });
  if (authFail) return authFail;

  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_ANON_KEY;
  if (!url || !key) return Response.json({ error: 'SUPABASE_URL / SUPABASE_ANON_KEY are not set on the dashboard server' }, { status: 500 });

  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return bad('body must be JSON');
  }

  const sosId = text(body.sosId, 120);
  const lat = num(body.lat);
  const lon = num(body.lon);
  if (!sosId) return bad('sosId is required');
  if (lat === null || lon === null || lat < -90 || lat > 90 || lon < -180 || lon > 180) return bad('lat and lon must be valid coordinates');

  const now = Date.now();
  const id = `dash-sosack-${randomUUID()}`;
  const event = {
    id,
    type: 'sos_state',
    lat,
    lon,
    featureRef: null,
    severity: null,
    waterLevel: null,
    authorId: 'dashboard',
    authorName: 'Dashboard',
    authorRole: 'official',
    timestampMs: now,
    expiresAt: now + SOS_TTL_MS,
    origin: 'server',
    hopCount: 0,
    note: null,
    disputeReason: null,
    payload: JSON.stringify({ sosId, state: 'ACKNOWLEDGED' }),
  };

  try {
    const res = await fetch(`${url}/rest/v1/events`, {
      method: 'POST',
      headers: { apikey: key, Authorization: `Bearer ${key}`, 'Content-Type': 'application/json', Prefer: 'return=minimal' },
      body: JSON.stringify(event),
    });
    if (!res.ok) return Response.json({ error: `Supabase returned ${res.status}` }, { status: 502 });
  } catch {
    return Response.json({ error: "Can't reach Supabase from the dashboard server" }, { status: 502 });
  }
  return Response.json({ ok: true, id });
}
