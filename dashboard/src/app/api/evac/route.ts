// Writes shelter events (evac_centre, evac_status) to Supabase on behalf of the dashboard operator.
// Runs on the dashboard's own server, so the PIN is checked here and the Supabase key never reaches the
// browser. Deliberately narrow: the browser sends a small typed request, and the server builds the whole
// event itself, so nothing else can be written through this door.
//
// Trust, stated plainly: the dashboard has one shared PIN and no roster seat, so anyone with the PIN can add,
// open or close a shelter for any municipality they type. That is a demo trade-off (the phone's role picker
// is the same); a phone's official, by contrast, is bound to their profile municipality. Writes are refused
// outright when DASHBOARD_PIN is not set, so a deployment with no PIN can never be written to.
import { randomUUID } from 'node:crypto';
import { checkDashboardPin } from '@/lib/dashboardAuth';

const DAY_MS = 24 * 60 * 60 * 1000;
const KINDS = new Set(['evacuation_centre', 'school', 'gym', 'barangay_hall', 'church', 'other']);
const STATUSES = new Set(['accepting', 'nearly_full', 'full', 'not_open']);
/** The phone's own bound: a shelter definition lives a year, a status a day. */
const CENTRE_TTL_MS = 365 * DAY_MS;

const bad = (message: string) => Response.json({ error: message }, { status: 400 });
const text = (v: unknown, max: number): string | null => (typeof v === 'string' && v.trim() && v.length <= max ? v.trim() : null);
const optionalText = (v: unknown, max: number): string | null | undefined =>
  v == null || v === '' ? null : typeof v === 'string' && v.length <= max ? v.trim() : undefined;
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

  const now = Date.now();
  const municipality = text(body.municipality, 120);
  const centreId = text(body.centreId, 80);
  if (!municipality) return bad('municipality is required');

  let type: string;
  let payload: Record<string, unknown>;
  let lat = 0;
  let lon = 0;
  let expiresAt: number;
  let id: string;

  if (body.kind === 'centre') {
    const name = text(body.name, 120);
    const la = num(body.lat);
    const lo = num(body.lon);
    const barangay = optionalText(body.barangay, 120);
    const capacity = body.capacityEstimate == null ? null : num(body.capacityEstimate);
    if (!name) return bad('name is required (up to 120 characters)');
    if (la === null || lo === null || la < -90 || la > 90 || lo < -180 || lo > 180) return bad('lat and lon must be valid coordinates');
    if (barangay === undefined) return bad('barangay is too long');
    if (capacity !== null && (capacity < 0 || capacity > 100_000)) return bad('capacityEstimate is out of range');
    const kind = typeof body.shelterKind === 'string' && KINDS.has(body.shelterKind) ? body.shelterKind : 'other';
    // Editing or removing needs the id of an existing shelter; adding makes a new one.
    id = `dash-centre-${randomUUID()}`;
    type = 'evac_centre';
    lat = la;
    lon = lo;
    expiresAt = now + CENTRE_TTL_MS;
    payload = {
      centreId: centreId ?? `evac-${randomUUID()}`,
      name,
      kind,
      lat: la,
      lon: lo,
      municipality,
      barangay,
      capacityEstimate: capacity !== null && capacity > 0 ? Math.round(capacity) : null,
      removed: body.removed === true,
    };
  } else if (body.kind === 'status') {
    const status = typeof body.status === 'string' && STATUSES.has(body.status) ? body.status : null;
    const occupancy = body.occupancy == null ? null : num(body.occupancy);
    if (!centreId) return bad('centreId is required');
    if (!status) return bad('status must be accepting, nearly_full, full or not_open');
    if (occupancy !== null && (occupancy < 0 || occupancy > 100_000)) return bad('occupancy is out of range');
    id = `dash-status-${randomUUID()}`;
    type = 'evac_status';
    expiresAt = now + DAY_MS;
    payload = { centreId, status, occupancy: occupancy === null ? null : Math.round(occupancy), municipality };
  } else {
    return bad("kind must be 'centre' or 'status'");
  }

  // The whole event is built here. The author is the dashboard, not a person: it says so on every phone.
  const event = {
    id,
    type,
    lat,
    lon,
    featureRef: null,
    severity: null,
    waterLevel: null,
    authorId: 'dashboard',
    authorName: 'Dashboard',
    authorRole: 'official',
    timestampMs: now,
    expiresAt,
    origin: 'server',
    hopCount: 0,
    note: null,
    disputeReason: null,
    payload: JSON.stringify(payload),
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
  return Response.json({ ok: true, id, centreId: payload.centreId });
}
