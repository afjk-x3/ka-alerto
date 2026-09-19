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

  const events: unknown[] = [];
  try {
    for (let page = 0; page < MAX_PAGES; page++) {
      const res = await fetch(
        `${url}/rest/v1/events?select=*&order=timestampMs.asc&limit=${PAGE}&offset=${page * PAGE}`,
        { headers: { apikey: key, Authorization: `Bearer ${key}` }, cache: 'no-store' },
      );
      if (!res.ok) return Response.json({ error: `Supabase returned ${res.status}` }, { status: 502 });
      const rows = (await res.json()) as unknown[];
      events.push(...rows);
      if (rows.length < PAGE) break;
    }
  } catch {
    return Response.json({ error: "Can't reach Supabase from the dashboard server" }, { status: 502 });
  }
  return Response.json({ events });
}
