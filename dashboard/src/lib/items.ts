import { Event } from './types';
import { summarizeAll, FeatureSummary } from './reducer';
import { describePlace, placeLine } from './gazetteer';

// Severity ladder and colours mirror android/.../ui/theme/SeverityColors.kt.
export const SEVERITY_LABEL: Record<string, string> = {
  S0: 'Cleared',
  S1: 'Passable, use caution',
  S2: 'Impassable for cars',
  S3: 'Impassable for all',
  SX: 'Conflicting reports',
};

const SOS_TYPES = new Set(['sos', 'sos_amend', 'sos_state']);

// Lowest to highest, same order as SosState.rank on the phone: the highest state seen wins.
const STATE_ORDER = [
  'DRAFT', 'QUEUED', 'BEACONING', 'UNREACHABLE', 'RELAYED', 'DELIVERED',
  'ACKNOWLEDGED', 'EN_ROUTE', 'ON_SCENE', 'RESCUED', 'CANCELLED', 'SAFE_SELF_RESOLVED',
];
const CLOSED_STATES = new Set(['RESCUED', 'CANCELLED', 'SAFE_SELF_RESOLVED']);

export const STATE_LABEL: Record<string, string> = {
  QUEUED: 'Queued',
  BEACONING: 'Broadcasting',
  UNREACHABLE: 'No channel reached yet',
  RELAYED: 'Relayed by a phone',
  DELIVERED: 'Delivered',
  ACKNOWLEDGED: 'Seen by a responder',
  EN_ROUTE: 'Help is on the way',
  ON_SCENE: 'Responder on scene',
  RESCUED: 'Rescued',
  CANCELLED: 'Cancelled',
  SAFE_SELF_RESOLVED: 'Marked safe',
};

const REPORT_LABEL: Record<string, string> = {
  flood_report: 'Flood report',
  confirm: 'Confirmed',
  dispute: 'Disputed',
  official_status: 'Official ruling',
};

export function reportLabel(type: string): string {
  return REPORT_LABEL[type] ?? type.replace(/_/g, ' ');
}

export interface SosContext {
  people?: string;
  companions?: string[];
  water?: string;
  trend?: string;
}

export interface SosStep {
  state: string;
  by: string;
  role: string;
  atMs: number;
}

export interface SosItem {
  kind: 'sos';
  id: string;
  lat: number;
  lon: number;
  state: string;
  closed: boolean;
  context: SosContext;
  history: SosStep[];
  startedAtMs: number;
  updatedAtMs: number;
  accuracyMeters?: number;
  origin: string;
  hopCount: number;
}

export interface ReportItem {
  kind: 'report';
  id: string;
  lat: number;
  lon: number;
  /** The spot's folded state (lib/reducer.ts), the same one a phone computes from the same events. */
  summary: FeatureSummary;
  stale: boolean;
  updatedAtMs: number;
  /** SHA-256 of the attached photo, if any (see ReportPhotoPayload on the phone). */
  photoHash?: string;
}

export type Item = SosItem | ReportItem;

interface Payload {
  photoHash?: string;
  sosId?: string;
  state?: string;
  accuracyMeters?: number;
  context?: SosContext;
}

function parsePayload(raw: string | null): Payload | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Payload;
  } catch {
    return null;
  }
}

export const BUCKET_LABEL: Record<string, string> = {
  confirmed: 'Confirmed',
  likely: 'Likely',
  unverified: 'Unverified',
  official: 'Official ruling',
};

/** How sure we are, in words: a conflict is its own state, never a confidence. */
export const statusLine = (s: FeatureSummary) => (s.isConflicted ? 'Conflicting reports' : (BUCKET_LABEL[s.bucket] ?? s.bucket));

export const latestReport = (s: FeatureSummary) => s.events.find((e) => e.type === 'flood_report');

export const reportCount = (s: FeatureSummary) => s.events.filter((e) => e.type === 'flood_report').length;

/** One item per SOS request (its sos, sos_amend and sos_state events folded together) plus one per flooded spot. */
export function buildItems(events: Event[], now = Date.now()): Item[] {
  const groups = new Map<string, Event[]>();
  const items: Item[] = [];

  for (const e of events) {
    if (SOS_TYPES.has(e.type)) {
      const sosId = parsePayload(e.payload)?.sosId;
      if (!sosId) continue;
      const g = groups.get(sosId);
      if (g) g.push(e);
      else groups.set(sosId, [e]);
    }
  }

  // Flood spots come from the phone's own fold (lib/reducer.ts), so a spot reads here as it does there.
  for (const s of summarizeAll(events, now)) {
    items.push({
      kind: 'report',
      id: s.featureRef,
      lat: s.lat,
      lon: s.lon,
      summary: s,
      stale: s.isStale,
      updatedAtMs: s.lastEventMs,
      photoHash: s.events.map((e) => (e.type === 'flood_report' ? parsePayload(e.payload)?.photoHash : undefined)).find(Boolean),
    });
  }

  for (const [sosId, group] of groups) {
    group.sort((a, b) => a.timestampMs - b.timestampMs);
    const request = group.find((e) => e.type === 'sos') ?? group[0];
    const context: SosContext = {};
    const history: SosStep[] = [];
    let state = 'QUEUED';
    let accuracyMeters: number | undefined;

    for (const e of group) {
      const p = parsePayload(e.payload);
      if (!p) continue;
      if (p.accuracyMeters != null && e.type === 'sos') accuracyMeters = p.accuracyMeters;
      if (e.type === 'sos_amend' && p.context) {
        for (const [k, v] of Object.entries(p.context)) {
          if (v != null && !(Array.isArray(v) && v.length === 0)) (context as Record<string, unknown>)[k] = v;
        }
      }
      if (e.type === 'sos_state' && p.state) {
        history.push({ state: p.state, by: e.authorName, role: e.authorRole, atMs: e.timestampMs });
        if (STATE_ORDER.indexOf(p.state) > STATE_ORDER.indexOf(state)) state = p.state;
      }
    }

    items.push({
      kind: 'sos',
      id: sosId,
      lat: request.lat,
      lon: request.lon,
      state,
      closed: CLOSED_STATES.has(state),
      context,
      history,
      startedAtMs: request.timestampMs,
      updatedAtMs: group[group.length - 1].timestampMs,
      accuracyMeters,
      origin: request.origin,
      hopCount: request.hopCount,
    });
  }

  return items;
}

/** Open SOS first (never buried under reports), then newest activity first. */
export function sortItems(items: Item[]): Item[] {
  const rank = (i: Item) => (i.kind === 'sos' ? (i.closed ? 1 : 0) : 2);
  return [...items].sort((a, b) => rank(a) - rank(b) || b.updatedAtMs - a.updatedAtMs);
}

export function timeAgo(ms: number, now = Date.now()): string {
  const s = Math.max(0, Math.floor((now - ms) / 1000));
  if (s < 60) return 'just now';
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} h ago`;
  return `${Math.floor(h / 24)} d ago`;
}

export interface Filters {
  /** Max age of last activity in ms; 0 = any. */
  ageMs: number;
  /** A flood-report severity (S0..SX), or '' for all. Reports only. */
  severity: string;
  /** SOS requests only. */
  sosState: 'all' | 'open' | 'closed';
  /** Free text: every word must appear somewhere in the item (see [searchText]). */
  query: string;
}

export const NO_FILTERS: Filters = { ageMs: 0, severity: '', sosState: 'all', query: '' };

export const isFiltering = (f: Filters) => f.ageMs > 0 || f.severity !== '' || f.sosState !== 'all' || f.query.trim() !== '';

/** Everything a search can match, lower-cased: place, coordinates, status, people and words. */
export function searchText(i: Item): string {
  const place = describePlace(i.lat, i.lon);
  const where = [place ? placeLine(place) : '', i.lat.toFixed(5), i.lon.toFixed(5)];
  if (i.kind === 'sos') {
    const c = i.context;
    return [
      'sos', i.state, STATE_LABEL[i.state], i.closed ? 'closed' : 'open', ...where,
      c.people, c.water, c.trend, ...(c.companions ?? []), ...i.history.map((h) => `${h.by} ${h.role}`),
    ].join(' ').toLowerCase();
  }
  const s = i.summary;
  return [
    s.severity, SEVERITY_LABEL[s.severity], statusLine(s), i.stale ? 'expired' : 'current', ...where,
    ...s.events.flatMap((e) => [e.authorName, e.authorRole, e.waterLevel, e.note]),
  ].filter(Boolean).join(' ').toLowerCase();
}

const matchesQuery = (i: Item, query: string) => {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length === 0) return true;
  const hay = searchText(i);
  return words.every((w) => hay.includes(w));
};

/** Each filter only touches the kind it names; age applies to both. */
export function applyFilters(items: Item[], f: Filters, now = Date.now()): Item[] {
  return items.filter((i) => {
    if (!matchesQuery(i, f.query)) return false;
    if (f.ageMs > 0 && now - i.updatedAtMs > f.ageMs) return false;
    if (i.kind === 'sos') return f.sosState === 'all' || (f.sosState === 'closed') === i.closed;
    return !f.severity || i.summary.severity === f.severity;
  });
}

const CSV_COLS = ['kind', 'id', 'status', 'lat', 'lon', 'reported_by', 'first_seen', 'last_update', 'people', 'water', 'note', 'how_sure', 'confidence_pct', 'reports', 'confirmations', 'disputes'];

/** Spreadsheet-safe: quotes every field and defuses leading = + - @ (names and notes are user-typed). */
const csvCell = (v: unknown) => {
  const s = v == null ? '' : String(v);
  return `"${(/^[=+\-@\t\r]/.test(s) ? `'${s}` : s).replace(/"/g, '""')}"`;
};

/** One row per flooded spot: its folded state, plus the newest report's author, water level and note. */
function spotRow(i: ReportItem) {
  const s = i.summary;
  const latest = latestReport(s);
  const first = Math.min(...s.events.filter((e) => e.type !== 'flood_withdraw').map((e) => e.timestampMs));
  return [
    'report', i.id, s.severity, s.lat, s.lon, latest?.authorName, new Date(first).toISOString(), new Date(s.lastEventMs).toISOString(),
    '', latest?.waterLevel, latest?.note, statusLine(s), s.isConflicted || s.bucket === 'official' ? '' : Math.round(s.confidence * 100), reportCount(s), s.confirmCount, s.disputeCount,
  ];
}

export function toCsv(items: Item[]): string {
  const rows = items.map((i) =>
    i.kind === 'sos'
      ? ['sos', i.id, i.state, i.lat, i.lon, '', new Date(i.startedAtMs).toISOString(), new Date(i.updatedAtMs).toISOString(), i.context.people, i.context.water, '', '', '', '', '', '']
      : spotRow(i),
  );
  return [CSV_COLS, ...rows].map((r) => r.map(csvCell).join(',')).join('\r\n');
}
