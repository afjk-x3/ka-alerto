// The dashboard's only data path. Runs on the dashboard's own server, so the PIN is
// checked here (not in browser code) and the Supabase key never reaches the browser.
// Demo-only gate: phones read the same table with the public anon key, so this guards the
// dashboard's door, not the data.
const PAGE = 1000; // PostgREST returns at most 1000 rows per request by default.
const MAX_PAGES = 20;

export async function GET(request: Request) {
  const pin = process.env.DASHBOARD_PIN;
  if (pin && request.headers.get('x-dashboard-pin') !== pin) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }

  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_ANON_KEY;
  if (!url || !key) {
    return Response.json({ error: 'SUPABASE_URL / SUPABASE_ANON_KEY are not set on the dashboard server' }, { status: 500 });
  }

  // Newest first: every reducer downstream (reducer.ts, items.ts, evac.ts) re-sorts by
  // timestampMs itself, so fetch order changes nothing there. It matters only at the cap
  // below — oldest-first meant a barangay past MAX_PAGES*PAGE events silently lost its
  // newest data, tonight's SOS included (found 21 Sep). `truncated` says so instead.
  const events: unknown[] = [];
  let truncated = false;
  try {
    for (let page = 0; page < MAX_PAGES; page++) {
      const res = await fetch(
        `${url}/rest/v1/events?select=*&order=timestampMs.desc&limit=${PAGE}&offset=${page * PAGE}`,
        { headers: { apikey: key, Authorization: `Bearer ${key}` }, cache: 'no-store' },
      );
      if (!res.ok) return Response.json({ error: `Supabase returned ${res.status}` }, { status: 502 });
      const rows = (await res.json()) as unknown[];
      events.push(...rows);
      if (rows.length < PAGE) break;
      if (page === MAX_PAGES - 1) truncated = true;
    }
  } catch {
    return Response.json({ error: "Can't reach Supabase from the dashboard server" }, { status: 502 });
  }
  return Response.json({ events, truncated });
}
