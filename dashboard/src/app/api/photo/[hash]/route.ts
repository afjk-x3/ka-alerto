// A report's photo, proxied from Supabase Storage so the PIN check stays on the dashboard's
// server. The bucket itself is public (phones upload and read with the anon key), so this
// guards the dashboard's door, not the file.
const HASH = /^[0-9a-f]{64}$/; // PhotoStore names a photo by its SHA-256, lowercase hex.

export async function GET(request: Request, { params }: { params: Promise<{ hash: string }> }) {
  const pin = process.env.DASHBOARD_PIN;
  if (pin && request.headers.get('x-dashboard-pin') !== pin) {
    return Response.json({ error: 'unauthorized' }, { status: 401 });
  }

  const { hash } = await params;
  if (!HASH.test(hash)) return Response.json({ error: 'bad hash' }, { status: 400 });

  const url = process.env.SUPABASE_URL;
  if (!url) return Response.json({ error: 'SUPABASE_URL is not set' }, { status: 500 });

  try {
    const res = await fetch(`${url}/storage/v1/object/public/photos/${hash}.jpg`, { cache: 'no-store' });
    if (!res.ok) return Response.json({ error: 'not uploaded' }, { status: 404 });
    return new Response(await res.arrayBuffer(), {
      headers: {
        'Content-Type': res.headers.get('content-type') ?? 'image/jpeg',
        // The name is the content hash, so a given URL never changes.
        'Cache-Control': 'private, max-age=86400, immutable',
      },
    });
  } catch {
    return Response.json({ error: "can't reach Supabase" }, { status: 502 });
  }
}
