// The bundled Philippine Standard Geographic Code list (dashboard/src/data/psgc.json, built by
// tools/psgc/build.py and identical to the phone's assets/psgc.json): municipalities and cities with their
// province, and their barangays. A port of the phone's Psgc.kt search. Names only, no coordinates.
export interface PsgcData {
  m: [string, string, string, number][];
  b: Record<string, string[]>;
}

/** Lower-case, accents and punctuation gone, so "Peñablanca" and "penablanca" compare equal. */
export const foldName = (text: string | null | undefined) =>
  (text ?? '').normalize('NFD').replace(/\p{Mn}+/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

/** "Brgy. Coral" -> "coral"; a bare "Brgy" (someone just started typing) -> "" so every barangay is offered. */
const bareName = (text: string | null | undefined) =>
  foldName(text).replace(/^(city of |municipality of |brgy |barangay )/, '').replace(/^(brgy|barangay)$/, '').replace(/ (city|pob)$/, '').trim();

const withoutPob = (barangay: string) => `Brgy. ${barangay.replace(/\s*\(Pob\.?\)\s*$/, '').trim()}`;

export function makePsgc(d: PsgcData) {
  const places = d.m.map(([code, name, province]) => ({ code, name, province, label: `${name}, ${province}` }));
  const byLabel = new Map(places.map((p) => [foldName(p.label), p]));
  const find = (label: string | null | undefined) => byLabel.get(foldName(label));
  const barangays = (label: string | null | undefined) => (d.b[find(label)?.code ?? ''] ?? []).map(withoutPob);

  return {
    find,
    /** Up to `limit` "Name, Province" labels: starting with what is typed first, then containing it. */
    search(query: string, limit = 6): string[] {
      const q = foldName(query);
      if (!q) return [];
      return places
        .filter((p) => foldName(p.label).includes(q))
        .sort((a, b) => Number(!foldName(a.label).startsWith(q)) - Number(!foldName(b.label).startsWith(q)) || a.name.localeCompare(b.name) || a.province.localeCompare(b.province))
        .slice(0, limit)
        .map((p) => p.label);
    },
    barangays,
    searchBarangays(label: string | null | undefined, query: string, limit = 8): string[] {
      const all = barangays(label);
      const q = bareName(query);
      if (!q) return all.slice(0, limit);
      return all
        .filter((b) => bareName(b).includes(q))
        .sort((a, b) => Number(!bareName(a).startsWith(q)) - Number(!bareName(b).startsWith(q)))
        .slice(0, limit);
    },
  };
}

export type Psgc = ReturnType<typeof makePsgc>;
