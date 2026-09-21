// Evacuation centre status, folded from `evac_status` events — a port of evacStates() in
// android/.../evac/EvacCentre.kt. The centre list is a copy of the phone's fixture
// (android/app/src/main/assets/evacuation_centres.json); keep the two in step.
// Read-only on purpose: officials post from their phones, where the role comes from a
// roster seat. The dashboard only has a shared PIN, so it must not be able to open a shelter.
import { Event } from './types';
import data from '@/data/evacuation_centres.json';

export interface Centre {
  id: string;
  name: string;
  lat: number;
  lon: number;
  kind: string;
  capacityEstimate?: number;
}

export type EvacStatus = 'accepting' | 'nearly_full' | 'not_open';

export const EVAC_LABEL: Record<EvacStatus, string> = {
  accepting: 'Accepting',
  nearly_full: 'Nearly full',
  not_open: 'Not open yet',
};

export interface EvacState {
  centre: Centre;
  status: EvacStatus;
  /** Head count inside, if the official gave one. */
  occupancy: number | null;
  updatedAtMs: number | null;
  updatedBy: string | null;
}

interface EvacPayload {
  centreId?: string;
  status?: string;
  occupancy?: number | null;
}

export const CENTRES: Centre[] = data.centres;

const isStatus = (s: string | undefined): s is EvacStatus => s === 'accepting' || s === 'nearly_full' || s === 'not_open';

/**
 * Latest update per centre wins; a centre nobody has opened is "not open yet", never
 * "accepting" — a building in the list is one that could be opened. An update older than its
 * own expiresAt is ignored: Supabase never purges, so without this a centre would read
 * "accepting" here long after phones have dropped it.
 */
export function buildEvacStates(events: Event[], now = Date.now()): EvacState[] {
  const latest = new Map<string, { e: Event; p: EvacPayload }>();
  for (const e of [...events].sort((a, b) => a.timestampMs - b.timestampMs)) {
    if (e.type !== 'evac_status' || (e.expiresAt > 0 && e.expiresAt < now) || !e.payload) continue;
    let p: EvacPayload;
    try {
      p = JSON.parse(e.payload) as EvacPayload;
    } catch {
      continue;
    }
    if (p.centreId) latest.set(p.centreId, { e, p });
  }

  const rank: Record<EvacStatus, number> = { accepting: 0, nearly_full: 1, not_open: 2 };
  return CENTRES.map((centre) => {
    const u = latest.get(centre.id);
    return {
      centre,
      status: isStatus(u?.p.status) ? u.p.status : 'not_open',
      occupancy: u?.p.occupancy ?? null,
      updatedAtMs: u?.e.timestampMs ?? null,
      updatedBy: u?.e.authorName ?? null,
    } satisfies EvacState;
  }).sort((a, b) => rank[a.status] - rank[b.status] || a.centre.name.localeCompare(b.centre.name));
}
