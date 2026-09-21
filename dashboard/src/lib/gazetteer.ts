// Names a point from data the dashboard already carries, a port of the phone's Gazetteer
// (android/.../location/Gazetteer.kt): the street centrelines and the shelters, so a name needs no
// network and no third party. Like the phone's it only knows the San Nicolas demo area; outside
// it a place has no name and callers fall back to coordinates. Keep the data files in step with
// the phone (src/data/streets.json, src/data/evacuation_centres.json).
import streets from '@/data/streets.json';
import { CENTRES } from './evac';
import { haversineMeters } from './reducer';

export interface PlaceName {
  /** The specific part first ("Sotto Street"). */
  primary: string;
  /** The wider area, when there is one. */
  secondary: string | null;
}

// DemoArea.kt's bounding box: not a surveyed boundary, the same claim the phone's map header makes.
const NORTH = 18.176;
const SOUTH = 18.166;
const EAST = 120.613;
const WEST = 120.599;
const BARANGAY = 'Brgy. San Juan Bautista';
const MUNICIPALITY = 'San Nicolas, Ilocos Norte';

/** A GPS fix indoors is often 20-40 m off; further than this from a street is not "on" it. */
const STREET_SNAP_M = 50;
/** Close enough to a named building to give directions by it. */
const LANDMARK_NEAR_M = 200;

export const inDemoArea = (lat: number, lon: number) => lat >= SOUTH && lat <= NORTH && lon >= WEST && lon <= EAST;

/** Metres from a point to a polyline of [lat, lon] points, on a local flat projection. */
function distanceToLineM(lat: number, lon: number, points: number[][]): number {
  const kx = 111_320 * Math.cos((lat * Math.PI) / 180);
  const ky = 110_574;
  const xy = points.map(([pLat, pLon]) => [(pLon - lon) * kx, (pLat - lat) * ky]);
  if (xy.length === 1) return Math.hypot(xy[0][0], xy[0][1]);
  let best = Infinity;
  for (let i = 1; i < xy.length; i++) {
    const [ax, ay] = xy[i - 1];
    const [bx, by] = xy[i];
    const dx = bx - ax;
    const dy = by - ay;
    const len2 = dx * dx + dy * dy;
    const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
    best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy));
  }
  return best;
}

export function describePlace(lat: number, lon: number): PlaceName | null {
  if (!inDemoArea(lat, lon)) return null;
  const area = `${BARANGAY}, ${MUNICIPALITY}`;

  let street: { name: string; m: number } | null = null;
  for (const s of streets.streets) {
    const m = distanceToLineM(lat, lon, s.points);
    if (m <= STREET_SNAP_M && (!street || m < street.m)) street = { name: s.name, m };
  }
  if (street) return { primary: street.name, secondary: area };

  let landmark: { name: string; m: number } | null = null;
  for (const c of CENTRES) {
    const m = haversineMeters(lat, lon, c.lat, c.lon);
    if (m <= LANDMARK_NEAR_M && (!landmark || m < landmark.m)) landmark = { name: c.name, m };
  }
  if (landmark) return { primary: `Near ${landmark.name}`, secondary: area };

  return { primary: BARANGAY, secondary: MUNICIPALITY };
}

export const placeLine = (p: PlaceName) => [p.primary, p.secondary].filter(Boolean).join(', ');

/** What a list row says about where: the street or landmark, else compact coordinates. */
export function whereShort(lat: number, lon: number): string {
  return describePlace(lat, lon)?.primary ?? `${lat.toFixed(4)}, ${lon.toFixed(4)}`;
}
