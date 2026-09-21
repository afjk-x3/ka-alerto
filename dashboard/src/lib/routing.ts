// Route alternatives from OSRM's free public demo server, ranked by how many CURRENT flooded
// reports lie along each route. OSRM knows roads, not floods: a road with no report simply
// looks clear, so this ranks by the reports we have, it does not certify a route as safe.
// Demo-only dependency: the public server is best-effort. Self-host OSRM (or swap the URL)
// if this ever matters beyond a demo.
import { Item } from './items';

const OSRM = 'https://router.project-osrm.org/route/v1/driving';
/** A report within this many metres of the route line counts as "along" it. */
const NEAR_M = 75;

export interface LatLon {
  lat: number;
  lon: number;
}

export interface FloodPoint extends LatLon {
  sev: 'S2' | 'S3' | 'SX';
}

export interface RouteOption {
  coords: [number, number][]; // [lon, lat]
  distanceM: number;
  durationS: number;
  s3: number;
  s2: number;
  sx: number;
  safest: boolean;
}

/** Current flooded reports worth avoiding: S3 (impassable), S2 (impassable for cars), SX (conflicting). */
export function floodPoints(items: Item[]): FloodPoint[] {
  const out: FloodPoint[] = [];
  for (const i of items) {
    if (i.kind !== 'report' || i.stale) continue;
    const sev = i.summary.severity;
    if (sev === 'S3' || sev === 'S2' || sev === 'SX') out.push({ lat: i.lat, lon: i.lon, sev });
  }
  return out;
}

/** Metres from point p to segment a-b, on a local flat approximation (fine at city scale). */
function distToSegmentM(p: LatLon, a: [number, number], b: [number, number]): number {
  const kx = 111_320 * Math.cos((p.lat * Math.PI) / 180);
  const ky = 110_540;
  const ax = (a[0] - p.lon) * kx, ay = (a[1] - p.lat) * ky;
  const bx = (b[0] - p.lon) * kx, by = (b[1] - p.lat) * ky;
  const dx = bx - ax, dy = by - ay;
  const len2 = dx * dx + dy * dy;
  const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
  return Math.hypot(ax + t * dx, ay + t * dy);
}

function nearRoute(p: LatLon, coords: [number, number][]): boolean {
  for (let i = 1; i < coords.length; i++) {
    if (distToSegmentM(p, coords[i - 1], coords[i]) <= NEAR_M) return true;
  }
  return false;
}

const score = (r: RouteOption) => r.s3 * 3 + r.s2 * 2 + r.sx;

export async function fetchRoutes(from: LatLon, to: LatLon, floods: FloodPoint[]): Promise<RouteOption[]> {
  const url = `${OSRM}/${from.lon},${from.lat};${to.lon},${to.lat}?alternatives=3&overview=full&geometries=geojson&steps=false`;
  let res: Response;
  try {
    res = await fetch(url);
  } catch {
    throw new Error("Couldn't reach the routing service. Check the internet connection.");
  }
  if (!res.ok) throw new Error(`Routing service error (${res.status})`);
  const body = (await res.json()) as {
    code: string;
    routes?: { distance: number; duration: number; geometry: { coordinates: [number, number][] } }[];
  };
  if (body.code !== 'Ok' || !body.routes?.length) throw new Error('No road route was found between here and that location.');

  // Reports at the destination are the reason for going there, not an obstacle on the way.
  const obstacles = floods.filter((f) => Math.hypot((f.lat - to.lat) * 110_540, (f.lon - to.lon) * 111_320 * Math.cos((to.lat * Math.PI) / 180)) > NEAR_M);

  const options: RouteOption[] = body.routes.map((r) => {
    const coords = r.geometry.coordinates;
    const hit = obstacles.filter((f) => nearRoute(f, coords));
    return {
      coords,
      distanceM: r.distance,
      durationS: r.duration,
      s3: hit.filter((f) => f.sev === 'S3').length,
      s2: hit.filter((f) => f.sev === 'S2').length,
      sx: hit.filter((f) => f.sev === 'SX').length,
      safest: false,
    };
  });
  options.sort((a, b) => score(a) - score(b) || a.durationS - b.durationS);
  options[0].safest = true;
  return options;
}

export function currentPosition(): Promise<LatLon> {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) return reject(new Error("This browser can't share a location."));
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lon: p.coords.longitude }),
      (e) =>
        reject(
          new Error(
            e.code === e.PERMISSION_DENIED
              ? 'Location is blocked for this page. Allow it in the browser to get routes from where you are.'
              : "Couldn't get your location.",
          ),
        ),
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  });
}

/** Where a route starts. "me" is this computer (the browser's location); "station" is a saved start; "pick" is a click on the map. */
export type OriginMode = 'me' | 'station' | 'pick';

export interface OriginState {
  mode: OriginMode;
  picked: LatLon | null;
  station: LatLon | null;
  /** The next map click sets `picked`. */
  picking: boolean;
}

export const NO_ORIGIN: OriginState = { mode: 'me', picked: null, station: null, picking: false };

/** The chosen start, or null for "ask the browser where this computer is". */
export const originOf = (o: OriginState): LatLon | null => (o.mode === 'station' ? o.station : o.mode === 'pick' ? o.picked : null);

/** True when the mode needs a point that has not been set yet. */
export const originMissing = (o: OriginState) => o.mode !== 'me' && originOf(o) === null;

export const googleDirectionsUrl = (to: LatLon, from?: LatLon | null) =>
  // No origin given: Google Maps starts from the viewer's current location and lists alternatives.
  `https://www.google.com/maps/dir/?api=1${from ? `&origin=${from.lat},${from.lon}` : ''}&destination=${to.lat},${to.lon}&travelmode=driving`;

export function describeRoute(r: RouteOption): string {
  const km = (r.distanceM / 1000).toFixed(1);
  const min = Math.max(1, Math.round(r.durationS / 60));
  return `${km} km · ${min} min`;
}

export interface RoutingState {
  status: 'idle' | 'locating' | 'loading' | 'done' | 'error';
  error?: string;
  options: RouteOption[];
  active: number;
}

export const NO_ROUTES: RoutingState = { status: 'idle', options: [], active: 0 };
