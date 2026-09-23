// What the map frames when the dashboard first loads. Framing every item is useless once the data
// spans regions (the live table has Pangasinan and Ilocos Norte, about 200 km apart): every marker
// shrinks to a dot. "Show all" still frames everything; this is only the opening view.
import type { Item } from './items';
// The .ts extension lets the Node check import this file directly (see tsconfig allowImportingTsExtensions).
import { haversineMeters } from './reducer.ts';

const DAY_MS = 24 * 60 * 60 * 1000;
/** Items this close to the newest one are the same "area" for the opening view. */
const NEAR_NEWEST_M = 100_000;

/**
 * Open SOS requests and the last day's activity first, since that is what an operator opening the
 * console needs to see. With none of either, the newest item and whatever is within 100 km of it.
 */
export function initialFocus(all: Item[], now = Date.now()): Item[] {
  // 0,0 is an SOS still waiting for GPS (lib/items.ts hasLocation) — nothing to frame.
  const list = all.filter((i) => !(i.lat === 0 && i.lon === 0));
  if (list.length === 0) return list;
  const recent = list.filter((i) => (i.kind === 'sos' && !i.closed) || now - i.updatedAtMs <= DAY_MS);
  if (recent.length > 0) return recent;
  const newest = list.reduce((a, b) => (b.updatedAtMs > a.updatedAtMs ? b : a));
  return list.filter((i) => haversineMeters(newest.lat, newest.lon, i.lat, i.lon) <= NEAR_NEWEST_M);
}
