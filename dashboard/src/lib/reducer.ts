// A port of the phone's reducer (android/.../data/Reducer.kt, Withdraw.kt, Severity.kt, GeoUtil.kt),
// so the dashboard shows the same status for a spot as a phone holding the same events (NFR-4).
// Rules A–D, role x proximity x decay weighting, Wilson confidence and the buckets are copied
// line for line, with the same constants. Keep the two in step: `npm run check` runs the phone's
// reducer test cases (checks/reducer.check.mts) against this file.
//
// Deliberately imports only types, so the check script can run it under plain Node.
import type { Event } from './types';

export type Bucket = 'confirmed' | 'likely' | 'unverified' | 'official';

/** Everything the dashboard needs to render one flooded spot. Mirrors the phone's FeatureSummary. */
export interface FeatureSummary {
  featureRef: string;
  lat: number;
  lon: number;
  /** S0..S3, or "SX" for a Rule C conflict. */
  severity: string;
  confidence: number;
  bucket: Bucket;
  isConflicted: boolean;
  lastEventMs: number;
  isStale: boolean;
  confirmCount: number;
  disputeCount: number;
  /** Full history for this spot, most recent first, including withdrawals. */
  events: Event[];
  officialSeverity: string | null;
  officialAuthorName: string | null;
  officialAtMs: number | null;
  /** An official asked to lower a contradicted spot and is waiting for a second official. */
  pendingSecondOfficial: boolean;
  /** Residents whose latest report is worse than the official severity. */
  contradictingCount: number;
}

const PROXIMATE_METERS = 500;
const WEIGHT_FLOOR = 0.05;
const CONFLICT_WEIGHT_THRESHOLD = 0.5;
const DEESCALATION_COUNT = 2;
const REQUIRED_OFFICIALS_TO_DEESCALATE = 2;
const EARTH_RADIUS_M = 6_371_000;

const ORDER = ['S0', 'S1', 'S2', 'S3'];
/** S0..S3 low to high. "SX" is deliberately not on the ladder (ordinal 0), as on the phone. */
export const severityOrdinal = (s: string) => Math.max(0, ORDER.indexOf(s));

/** Severity-dependent TTL in minutes; also the decay time constant tau. */
const ttlMinutesFor = (s: string | null) => (s === 'S1' ? 120 : s === 'S2' ? 240 : s === 'S3' ? 360 : 60);

const TYPE_FLOOD_WITHDRAW = 'flood_withdraw';

export function haversineMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

const roleWeight = (role: string) => (role === 'official' ? 5 : role === 'responder' ? 2.5 : 1);

const proximityFactor = (m: number) => (m <= 100 ? 1 : m <= PROXIMATE_METERS ? 0.7 : m <= 2000 ? 0.4 : 0.2);

const recencyFactor = (ageMs: number, severity: string) => Math.exp(-(ageMs / 60_000) / ttlMinutesFor(severity));

/** Wilson score lower bound on weighted counts, z about 1.44 (85%). */
function wilsonLowerBound(agree: number, disagree: number): number {
  const n = agree + disagree;
  if (n <= 0) return 0;
  const z = 1.44;
  const z2 = z * z;
  const pHat = agree / n;
  const numerator = pHat + z2 / (2 * n) - z * Math.sqrt((pHat * (1 - pHat)) / n + z2 / (4 * n * n));
  const denominator = 1 + z2 / n;
  return Math.min(1, Math.max(0, numerator / denominator));
}

/** Each author's events newer than their own latest withdrawal, and never the withdrawals themselves. */
export function liveEvents(featureEvents: Event[]): Event[] {
  const withdrawnAt = new Map<string, number>();
  for (const e of featureEvents) {
    if (e.type === TYPE_FLOOD_WITHDRAW) withdrawnAt.set(e.authorId, Math.max(withdrawnAt.get(e.authorId) ?? -Infinity, e.timestampMs));
  }
  return featureEvents.filter((e) => e.type !== TYPE_FLOOD_WITHDRAW && e.timestampMs > (withdrawnAt.get(e.authorId) ?? -Infinity));
}

interface Weighted {
  event: Event;
  severity: string;
  weight: number;
  proximate: boolean;
}

interface Resolution {
  severity: string;
  conflicted: boolean;
  bucket: Bucket;
  confidence: number;
  /** The highest severity any weighted report claims, before Rules B and C resolve it. */
  maxSeverity: string;
}

const maxBy = <T>(xs: T[], key: (x: T) => number): T | undefined =>
  xs.reduce<T | undefined>((best, x) => (best === undefined || key(x) > key(best) ? x : best), undefined);

function resolveOfficial(officialEvent: Event, weightBySeverity: Map<string, number>): Resolution {
  const severity = officialEvent.severity!;
  const total = [...weightBySeverity.values()].reduce((a, b) => a + b, 0);
  const agree = weightBySeverity.get(severity) ?? 0;
  return { severity, conflicted: false, bucket: 'official', confidence: total > 0 ? agree / total : 1, maxSeverity: severity };
}

function resolveCrowd(weighted: Weighted[], weightBySeverity: Map<string, number>): Resolution {
  const withWeight = [...weightBySeverity.entries()].filter(([, w]) => w > WEIGHT_FLOOR).map(([s]) => s);
  if (withWeight.length === 0) return { severity: 'S0', conflicted: false, bucket: 'unverified', confidence: 0, maxSeverity: 'S0' };

  // Rule A: the highest claimed tier is the ceiling.
  const maxSeverity = maxBy(withWeight, severityOrdinal)!;

  // Rule B: only a lower tier with >= 2 proximate observers can pull the display down.
  const lowerEligible = withWeight
    .filter((s) => severityOrdinal(s) < severityOrdinal(maxSeverity))
    .filter((s) => weighted.filter((w) => w.severity === s && w.proximate).length >= DEESCALATION_COUNT);

  const dangerWeight = (weightBySeverity.get('S2') ?? 0) + (weightBySeverity.get('S3') ?? 0);
  const safeWeight = (weightBySeverity.get('S0') ?? 0) + (weightBySeverity.get('S1') ?? 0);
  // Rule C: genuine disagreement is shown, not averaged; a met Rule B bar pre-empts it.
  const conflicted = lowerEligible.length === 0 && dangerWeight >= CONFLICT_WEIGHT_THRESHOLD && safeWeight >= CONFLICT_WEIGHT_THRESHOLD;

  const resolved = conflicted ? 'SX' : lowerEligible.length > 0 ? maxBy(lowerEligible, severityOrdinal)! : maxSeverity;

  if (conflicted) return { severity: resolved, conflicted: true, bucket: 'unverified', confidence: 0, maxSeverity };

  // "Unverified: confidence < 0.35, OR a single report" — a lone observer never reads as more.
  if (weighted.length <= 1) {
    const w = weightBySeverity.get(resolved);
    return { severity: resolved, conflicted: false, bucket: 'unverified', confidence: w === undefined ? 0 : wilsonLowerBound(w, 0), maxSeverity };
  }

  const agree = weightBySeverity.get(resolved) ?? 0;
  const disagree = [...weightBySeverity.entries()].filter(([s]) => s !== resolved).reduce((a, [, w]) => a + w, 0);
  const confidence = wilsonLowerBound(agree, disagree);
  const proximateAgreeing = weighted.filter((w) => w.severity === resolved && w.proximate).length;
  const bucket: Bucket = confidence >= 0.65 && proximateAgreeing >= 2 ? 'confirmed' : confidence >= 0.35 ? 'likely' : 'unverified';
  return { severity: resolved, conflicted: false, bucket, confidence, maxSeverity };
}

/**
 * Pure fold over one spot's events into its display state. `allEvents` is expected newest first, as
 * the phone's own query returns them; only an edge case (a spot with no flood_report left) reads
 * the order.
 */
export function summarize(featureRef: string, allEvents: Event[], now: number): FeatureSummary | null {
  return fold(featureRef, allEvents.filter((e) => e.featureRef === featureRef), now);
}

function fold(featureRef: string, featureEvents: Event[], now: number): FeatureSummary | null {
  if (featureEvents.length === 0) return null;
  const events = liveEvents(featureEvents);
  // Nothing with a reading is left: the spot leaves the map instead of showing an S0 nobody reported.
  if (!events.some((e) => e.severity != null)) return null;

  // One live position per author, so nobody stacks repeated confirms to fake consensus.
  const byAuthor = new Map<string, Event[]>();
  for (const e of events) byAuthor.set(e.authorId, [...(byAuthor.get(e.authorId) ?? []), e]);
  const latestPerAuthor = [...byAuthor.values()].map((es) => maxBy(es, (e) => e.timestampMs)!);

  // Anchor: the flood_reports' centroid, never a confirm's own position.
  const reports = events.filter((e) => e.type === 'flood_report');
  const avg = (xs: number[]) => xs.reduce((a, b) => a + b, 0) / xs.length;
  const anchorLat = reports.length ? avg(reports.map((e) => e.lat)) : events[0].lat;
  const anchorLon = reports.length ? avg(reports.map((e) => e.lon)) : events[0].lon;

  const weighted: Weighted[] = [];
  for (const event of latestPerAuthor) {
    if (event.severity == null) continue;
    const ageMs = Math.max(0, now - event.timestampMs);
    const proximity = proximityFactor(haversineMeters(anchorLat, anchorLon, event.lat, event.lon));
    weighted.push({
      event,
      severity: event.severity,
      weight: roleWeight(event.authorRole) * proximity * recencyFactor(ageMs, event.severity),
      proximate: proximity >= 0.7,
    });
  }
  // An official's own ruling is not also counted inside the crowd it overrides.
  const crowdWeighted = weighted.filter((w) => w.event.authorRole !== 'official');
  const weightBySeverity = new Map<string, number>();
  for (const w of crowdWeighted) weightBySeverity.set(w.severity, (weightBySeverity.get(w.severity) ?? 0) + w.weight);

  const crowd = resolveCrowd(crowdWeighted, weightBySeverity);

  // Rule D: an official event overrides the crowd, with the second-official gate as its one exception.
  const officialEvents = events.filter((e) => e.authorRole === 'official' && e.severity != null);
  const latestOfficial = maxBy(officialEvents, (e) => e.timestampMs);
  const officialSeverity = latestOfficial?.severity ?? null;

  const contradicting = officialSeverity
    ? crowdWeighted.filter((w) => severityOrdinal(w.severity) > severityOrdinal(officialSeverity) && w.weight > WEIGHT_FLOOR).length
    : 0;

  // The gate only holds back a de-escalation of a contradicted spot.
  const isDeEscalation = officialSeverity != null && severityOrdinal(officialSeverity) < severityOrdinal(crowd.maxSeverity);
  const gated = isDeEscalation && contradicting >= DEESCALATION_COUNT;
  const backingOfficials = new Set(officialEvents.filter((e) => e.severity === officialSeverity).map((e) => e.authorId)).size;
  const officialInForce = latestOfficial !== undefined && (!gated || backingOfficials >= REQUIRED_OFFICIALS_TO_DEESCALATE);

  const resolution = officialInForce ? resolveOfficial(latestOfficial!, weightBySeverity) : crowd;

  return {
    featureRef,
    lat: anchorLat,
    lon: anchorLon,
    severity: resolution.severity,
    confidence: resolution.confidence,
    bucket: resolution.bucket,
    isConflicted: resolution.conflicted,
    lastEventMs: Math.max(...events.map((e) => e.timestampMs)),
    isStale: now > Math.max(...events.map((e) => e.expiresAt)),
    confirmCount: events.filter((e) => e.type === 'confirm').length,
    disputeCount: events.filter((e) => e.type === 'dispute').length,
    officialSeverity,
    officialAuthorName: latestOfficial?.authorName ?? null,
    officialAtMs: latestOfficial?.timestampMs ?? null,
    pendingSecondOfficial: latestOfficial !== undefined && gated && !officialInForce,
    contradictingCount: contradicting,
    events: [...events, ...featureEvents.filter((e) => e.type === TYPE_FLOOD_WITHDRAW)].sort((a, b) => b.timestampMs - a.timestampMs),
  };
}

/** One summary per spot, grouping the log once instead of filtering it per spot. Events with no spot are ignored. */
export function summarizeAll(allEvents: Event[], now: number): FeatureSummary[] {
  const sorted = [...allEvents].sort((a, b) => b.timestampMs - a.timestampMs);
  const groups = new Map<string, Event[]>();
  for (const e of sorted) {
    if (!e.featureRef) continue;
    const g = groups.get(e.featureRef);
    if (g) g.push(e);
    else groups.set(e.featureRef, [e]);
  }
  return [...groups].map(([ref, evs]) => fold(ref, evs, now)).filter((s): s is FeatureSummary => s !== null);
}
