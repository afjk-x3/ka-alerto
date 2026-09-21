// Evacuation centres and their status, folded from events: a port of the phone's fold
// (android/.../evac/EvacCentre.kt: resolveCentres and evacStates), so the dashboard shows the same shelters
// and states a phone holding the same events would. `npm run check` (checks/evac.check.mts) runs the phone's
// test cases against it.
//
// The bundled four are a copy of the phone's fixture (android/app/src/main/assets/evacuation_centres.json).
// Officials add more with `evac_centre` events and open/close them with `evac_status` events; both can now be
// written from here (src/app/api/evac/route.ts) as well as from a phone. A shelter belongs to the municipality
// that created it, and only that municipality's updates count.
import type { Event } from './types';
// A relative path with an import attribute, so the Node check (checks/evac.check.mts) can load it too.
import data from '../data/evacuation_centres.json' with { type: 'json' };

export interface Centre {
  id: string;
  name: string;
  lat: number;
  lon: number;
  kind: string;
  capacityEstimate?: number;
  /** "San Nicolas, Ilocos Norte". */
  municipality: string;
  barangay: string | null;
  /** True for a shelter an official added; false for one of the bundled four. */
  custom: boolean;
}

export type EvacStatus = 'accepting' | 'nearly_full' | 'not_open';

export const EVAC_LABEL: Record<EvacStatus, string> = {
  accepting: 'Accepting',
  nearly_full: 'Nearly full',
  not_open: 'Not open yet',
};

export const EVAC_KINDS = ['school', 'gym', 'barangay_hall', 'church', 'other'] as const;
export const KIND_LABEL: Record<string, string> = {
  school: 'School',
  gym: 'Gym',
  barangay_hall: 'Barangay hall',
  church: 'Church',
  other: 'Other',
};

export interface EvacState {
  centre: Centre;
  status: EvacStatus;
  /** Head count inside, if the official gave one. */
  occupancy: number | null;
  updatedAtMs: number | null;
  updatedBy: string | null;
}

export interface EvacCentrePayload {
  centreId: string;
  name: string;
  kind?: string;
  lat: number;
  lon: number;
  municipality: string;
  barangay?: string | null;
  capacityEstimate?: number | null;
  removed?: boolean;
}

interface EvacStatusPayload {
  centreId?: string;
  status?: string;
  occupancy?: number | null;
  municipality?: string | null;
}

/** Only the demo area has bundled shelters, so it is where the dashboard starts. */
export const DEMO_MUNICIPALITY = 'San Nicolas, Ilocos Norte';
const BUNDLED_MUNICIPALITY = DEMO_MUNICIPALITY;
const BUNDLED_BARANGAY = 'Brgy. San Juan Bautista';

/** The four bundled shelters, as the phone loads them (municipality and barangay filled in). */
export const CENTRES: Centre[] = data.centres.map((c) => ({
  id: c.id,
  name: c.name,
  lat: c.lat,
  lon: c.lon,
  kind: c.kind,
  capacityEstimate: c.capacityEstimate,
  municipality: BUNDLED_MUNICIPALITY,
  barangay: BUNDLED_BARANGAY,
  custom: false,
}));

/** Lower-cased, trimmed, inner whitespace collapsed. */
export const normalizePlace = (text: string | null | undefined) => (text ?? '').trim().toLowerCase().replace(/\s+/g, ' ');

/** Two municipalities are the same when they normalise to the same non-empty text. */
export function sameMunicipality(a: string | null | undefined, b: string | null | undefined): boolean {
  const na = normalizePlace(a);
  return na !== '' && na === normalizePlace(b);
}

/** Whoever is acting for a municipality may change only that municipality's shelters; with none set, none. */
export const canManage = (acting: string | null | undefined, centre: Centre) => sameMunicipality(acting, centre.municipality);

function parse<T>(raw: string | null): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

/**
 * The bundled shelters plus the ones officials added. The latest `evac_centre` event per id wins, ordered by
 * time and then event id so two devices with the same events agree; a later event from another municipality
 * is ignored, so one municipality cannot rewrite or remove another's shelter; a removed shelter disappears;
 * an event reusing a bundled id is ignored.
 */
export function resolveCentres(events: Event[]): Centre[] {
  const bundledIds = new Set(CENTRES.map((c) => c.id));
  const history = new Map<string, { ts: number; id: string; p: EvacCentrePayload }[]>();
  for (const e of events) {
    if (e.type !== 'evac_centre') continue;
    const p = parse<EvacCentrePayload>(e.payload);
    if (!p || !p.centreId || bundledIds.has(p.centreId) || !p.name?.trim() || !p.municipality?.trim()) continue;
    const list = history.get(p.centreId) ?? [];
    list.push({ ts: e.timestampMs, id: e.id, p });
    history.set(p.centreId, list);
  }
  const added: Centre[] = [];
  for (const [id, list] of history) {
    list.sort((a, b) => a.ts - b.ts || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
    const owner = list[0].p.municipality;
    const live = [...list].reverse().find((h) => sameMunicipality(h.p.municipality, owner))!.p;
    if (live.removed) continue;
    added.push({
      id,
      name: live.name.trim(),
      lat: live.lat,
      lon: live.lon,
      kind: live.kind ?? 'other',
      capacityEstimate: live.capacityEstimate ?? undefined,
      municipality: owner.trim(),
      barangay: live.barangay?.trim() || null,
      custom: true,
    });
  }
  return [...CENTRES, ...added];
}

const isStatus = (s: string | undefined): s is EvacStatus => s === 'accepting' || s === 'nearly_full' || s === 'not_open';

/**
 * Latest update per shelter wins; a shelter nobody has opened is "not open yet", never "accepting". An update
 * counts only for a shelter that exists and, when it says which municipality it is from, only if that is the
 * shelter's own. An update older than its own expiresAt is ignored: Supabase never purges, so without this a
 * shelter would read "accepting" here long after phones have dropped it.
 */
export function buildEvacStates(events: Event[], now = Date.now()): EvacState[] {
  const centres = resolveCentres(events);
  const municipalityOf = new Map(centres.map((c) => [c.id, c.municipality]));
  const latest = new Map<string, { e: Event; p: EvacStatusPayload }>();
  const ordered = [...events].sort((a, b) => a.timestampMs - b.timestampMs || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  for (const e of ordered) {
    if (e.type !== 'evac_status' || (e.expiresAt > 0 && e.expiresAt < now)) continue;
    const p = parse<EvacStatusPayload>(e.payload);
    if (!p?.centreId || !municipalityOf.has(p.centreId)) continue;
    if (p.municipality != null && !sameMunicipality(p.municipality, municipalityOf.get(p.centreId))) continue;
    latest.set(p.centreId, { e, p });
  }

  const rank: Record<EvacStatus, number> = { accepting: 0, nearly_full: 1, not_open: 2 };
  return centres
    .map((centre) => {
      const u = latest.get(centre.id);
      return {
        centre,
        status: isStatus(u?.p.status) ? u.p.status : 'not_open',
        occupancy: u?.p.occupancy ?? null,
        updatedAtMs: u?.e.timestampMs ?? null,
        updatedBy: u?.e.authorName ?? null,
      } satisfies EvacState;
    })
    .sort((a, b) => rank[a.status] - rank[b.status] || a.centre.name.localeCompare(b.centre.name));
}
