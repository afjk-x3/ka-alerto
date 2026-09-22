'use client';

import { useEffect, useState } from 'react';
import { fetchPhoto } from '@/lib/api';
import { RoutingState, OriginMode, OriginState, describeRoute, googleDirectionsUrl, originOf, originMissing } from '@/lib/routing';
import { Item, SosItem, ReportItem, STATE_LABEL, STATE_ORDER, SEVERITY_LABEL, latestReport, reportCount, statusLine, timeAgo } from '@/lib/items';
import type { Event } from '@/lib/types';
import { describePlace, placeLine } from '@/lib/gazetteer';

/** What the Directions section needs to let the operator choose where a route starts. */
export interface OriginControl {
  state: OriginState;
  setMode: (m: OriginMode) => void;
  saveStation: () => void;
  /** Throw away the picked start and click a new one. */
  repick: () => void;
}

const ORIGIN_LABEL: Record<OriginMode, string> = { me: 'This computer', station: 'Station', pick: 'Pick on map' };
const at = (p: { lat: number; lon: number }) => `${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}`;

interface DetailProps {
  item: Item | null;
  onClose: () => void;
  originCtl: OriginControl;
  routing: RoutingState;
  onFindRoutes: () => void;
  onPickRoute: (i: number) => void;
  onAcknowledge: (item: SosItem) => Promise<void>;
}

/** A field nobody filled in is left out rather than shown as a dash. */
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  if (children == null || children === '') return null;
  return (
    <div className="field">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

/** The place name when the demo-area gazetteer knows it, and always the coordinates. */
function Coords({ lat, lon }: { lat: number; lon: number }) {
  const place = describePlace(lat, lon);
  return (
    <>
      {place && <span className="place">{placeLine(place)}<br /></span>}
      <span className="mono">{lat.toFixed(5)}, {lon.toFixed(5)}</span>
    </>
  );
}

/** Directions from a start the operator chooses: a Google Maps handoff, plus routes ranked against current flood reports. */
function Directions({ lat, lon, routing, onFind, onPick, originCtl }: { lat: number; lon: number; routing: RoutingState; onFind: () => void; onPick: (i: number) => void; originCtl: OriginControl }) {
  const busy = routing.status === 'locating' || routing.status === 'loading';
  const { state, setMode, saveStation, repick } = originCtl;
  const from = originOf(state);
  const isStation = state.station && from && state.station.lat === from.lat && state.station.lon === from.lon;
  return (
    <section className="directions">
      <h3>Directions</h3>
      <div className="origin" role="group" aria-label="Start from">
        <span className="origin-label">Start from</span>
        <div className="seg">
          {(['me', 'station', 'pick'] as const).map((m) => (
            <button key={m} className={state.mode === m ? 'on' : ''} aria-pressed={state.mode === m} onClick={() => setMode(m)}>
              {ORIGIN_LABEL[m]}
            </button>
          ))}
        </div>
        {state.mode === 'me' && <p className="hint">Uses this computer&apos;s location. To send a rescue unit, choose Station or Pick on map.</p>}
        {state.mode === 'station' &&
          (state.station ? (
            <p className="hint">Station: <span className="mono">{at(state.station)}</span></p>
          ) : (
            <p className="hint">No station saved yet. Choose Pick on map, click its location, then Save as station.</p>
          ))}
        {state.mode === 'pick' &&
          (state.picking ? (
            <p className="hint">Click the map to set the start. Esc cancels.</p>
          ) : state.picked ? (
            <div className="origin-line">
              <span className="mono">{at(state.picked)}</span>
              <button className="btn" onClick={repick}>Choose again</button>
              {!isStation && <button className="btn" onClick={saveStation}>Save as station</button>}
            </div>
          ) : (
            <p className="hint">Click the map to set the start.</p>
          ))}
      </div>
      <div className="dir-actions">
        <a className="btn btn-primary dir-link" href={googleDirectionsUrl({ lat, lon }, from)} target="_blank" rel="noreferrer">
          Directions in Google Maps
        </a>
        <button className="btn" onClick={onFind} disabled={busy || originMissing(state)}>
          {routing.status === 'locating' ? 'Finding you…' : routing.status === 'loading' ? 'Finding routes…' : 'Show routes ranked by reported floods'}
        </button>
      </div>
      <p className="hint dir-hint">{from ? 'Google Maps opens with the chosen start and lists its own alternatives.' : 'Google Maps starts from your current location and lists its own alternatives.'}</p>
      {routing.status === 'error' && <div className="banner-error dir-error" role="alert">{routing.error}</div>}
      {routing.status === 'done' && (
        <>
          <ul className="routes">
            {routing.options.map((r, i) => {
              const flooded = r.s3 + r.s2 + r.sx;
              const parts = [r.s3 && `${r.s3} impassable (S3)`, r.s2 && `${r.s2} not for cars (S2)`, r.sx && `${r.sx} conflicting`].filter(Boolean);
              return (
                <li key={i}>
                  <button className={`route ${i === routing.active ? 'is-active' : ''}`} onClick={() => onPick(i)} aria-pressed={i === routing.active}>
                    <span className="route-head">
                      <b>Route {String.fromCharCode(65 + i)}</b>
                      {r.safest && <span className="pill pill-ok">Fewest flooded reports</span>}
                    </span>
                    <span className="route-sub">{describeRoute(r)}</span>
                    <span className={`route-flood ${flooded ? 'warn' : 'clear'}`}>
                      {flooded === 0 ? 'No flooded reports along this route' : `${parts.join(' · ')} along the way`}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
          <p className="hint">
            Ranked by the current flood reports we hold. A road nobody has reported can still be flooded, so check
            with people on the ground before sending anyone.
          </p>
        </>
      )}
    </section>
  );
}

function Photo({ hash }: { hash: string }) {
  const [src, setSrc] = useState<string | null | undefined>(undefined); // undefined = loading
  useEffect(() => {
    let url: string | null = null;
    let cancelled = false;
    setSrc(undefined);
    fetchPhoto(hash)
      .then((u) => {
        if (cancelled) {
          if (u) URL.revokeObjectURL(u);
          return;
        }
        url = u;
        setSrc(u);
      })
      .catch(() => !cancelled && setSrc(null));
    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [hash]);

  if (src === undefined) return <div className="photo photo-note">Loading photo…</div>;
  if (src === null) {
    return (
      <div className="photo photo-note">
        Photo not uploaded yet. Only the phone that took it has it until that phone gets a connection.
      </div>
    );
  }
  return (
    <a href={src} target="_blank" rel="noreferrer" title="Open full size">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="photo" src={src} alt="Photo attached to this report" />
    </a>
  );
}

const when = (ms: number) => new Date(ms).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });

type DirProps = Pick<DetailProps, 'routing' | 'onFindRoutes' | 'onPickRoute' | 'originCtl'>;

/** Whether an "Acknowledge" write would move anything — false once the request is already at or
 * past ACKNOWLEDGED (by anyone, phone or dashboard) or already closed. */
function canAcknowledge(item: SosItem): boolean {
  return !item.closed && STATE_ORDER.indexOf(item.state) < STATE_ORDER.indexOf('ACKNOWLEDGED');
}

/** The one write this panel can make (added 22 Sep 2026, review D3) — everything else about an SOS is
 * still read-only, same trust note as the shelter controls: one shared PIN, no roster seat. */
function AcknowledgeControl({ item, onAcknowledge }: { item: SosItem; onAcknowledge: (item: SosItem) => Promise<void> }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!canAcknowledge(item)) return null;
  return (
    <div className="sos-ack">
      <button
        className="btn btn-primary"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          setError(null);
          try {
            await onAcknowledge(item);
          } catch (e) {
            setError(e instanceof Error ? e.message : 'Could not save');
          } finally {
            setBusy(false);
          }
        }}
      >
        {busy ? 'Acknowledging…' : 'Acknowledge (LGU has seen this)'}
      </button>
      {error && <p className="hint" role="alert">{error}</p>}
    </div>
  );
}

function SosDetail({ item, routing, onFindRoutes, onPickRoute, originCtl, onAcknowledge }: { item: SosItem } & DirProps & Pick<DetailProps, 'onAcknowledge'>) {
  const c = item.context;
  return (
    <>
      <div className={`status-banner ${item.closed ? 'closed' : 'open'}`}>
        {STATE_LABEL[item.state] ?? item.state}
      </div>
      <AcknowledgeControl item={item} onAcknowledge={onAcknowledge} />
      <dl>
        <Field label="Location"><Coords lat={item.lat} lon={item.lon} /></Field>
        {item.accuracyMeters != null && <Field label="GPS accuracy">± {Math.round(item.accuracyMeters)} m</Field>}
        <Field label="Raised">{when(item.startedAtMs)} ({timeAgo(item.startedAtMs)})</Field>
        <Field label="People">{c.people}</Field>
        <Field label="With them">{c.companions?.length ? c.companions.join(', ') : undefined}</Field>
        <Field label="Water level">{c.water}</Field>
        <Field label="Water trend">{c.trend}</Field>
      </dl>
      {!c.people && !c.companions?.length && !c.water && !c.trend && (
        <p className="hint">The requester has not added details yet.</p>
      )}
      <Directions lat={item.lat} lon={item.lon} routing={routing} onFind={onFindRoutes} onPick={onPickRoute} originCtl={originCtl} />
      <p className="hint">
        Name and medical details are deliberately not sent — they stay on the requester&apos;s phone.
      </p>
      <h3>Response timeline</h3>
      {item.history.length === 0 ? (
        <p className="hint">No responder activity yet.</p>
      ) : (
        <ol className="timeline">
          {item.history.map((s, i) => (
            <li key={i}>
              <strong>{STATE_LABEL[s.state] ?? s.state}</strong>
              <span>{s.by}{s.role !== 'resident' ? ` (${s.role})` : ''} · {when(s.atMs)}</span>
            </li>
          ))}
        </ol>
      )}
    </>
  );
}

/** One line of a spot's history. */
function eventLabel(e: Event): string {
  switch (e.type) {
    case 'flood_report': return `Report${e.severity ? ` · ${e.severity}` : ''}${e.waterLevel ? ` · ${e.waterLevel}` : ''}`;
    case 'confirm': return 'Confirmation';
    case 'dispute': return `Dispute${e.disputeReason ? ` (${e.disputeReason.replace(/_/g, ' ')})` : ''}`;
    case 'official_status': return `Official ruling${e.severity ? ` · ${e.severity}` : ''}`;
    case 'flood_withdraw': return 'Withdrawn by its author';
    default: return e.type.replace(/_/g, ' ');
  }
}

function ReportDetail({ item, routing, onFindRoutes, onPickRoute, originCtl }: { item: ReportItem } & DirProps) {
  const s = item.summary;
  const latest = latestReport(s);
  const n = reportCount(s);
  const official = s.officialSeverity ? `${s.officialSeverity} by ${s.officialAuthorName ?? 'an official'}${s.officialAtMs ? `, ${timeAgo(s.officialAtMs)}` : ''}` : null;
  return (
    <>
      <div className={`status-banner ${item.stale ? '' : `sev-${s.severity}`}`}>
        {item.stale ? 'Expired · needs a fresh look' : `${s.severity} · ${SEVERITY_LABEL[s.severity] ?? s.severity}`}
      </div>
      {official && (
        <div className={`official-note ${s.pendingSecondOfficial ? 'pending' : ''}`}>
          {s.pendingSecondOfficial ? `Official ruling ${official} is waiting for a second official to agree.` : `Official ruling: ${official}.`}
          {s.contradictingCount > 0 ? ` ${s.contradictingCount} resident${s.contradictingCount === 1 ? '' : 's'} report worse.` : ''}
        </div>
      )}
      {item.photoHash && <Photo hash={item.photoHash} />}
      <dl>
        {!item.stale && (
          <Field label="How sure">
            {statusLine(s)}
            {s.isConflicted || s.bucket === 'official' ? '' : ` · ${Math.round(s.confidence * 100)}%`}
          </Field>
        )}
        <Field label="Water level">{latest?.waterLevel}</Field>
        <Field label="Location"><Coords lat={s.lat} lon={s.lon} /></Field>
        <Field label="Reports">{n} · {s.confirmCount} confirmed · {s.disputeCount} disputed</Field>
        <Field label="Last update">{when(s.lastEventMs)} ({timeAgo(s.lastEventMs)})</Field>
        <Field label="Status">{item.stale ? 'Expired — needs a fresh look' : 'Current'}</Field>
        <Field label="Note">{latest?.note || undefined}</Field>
      </dl>
      <Directions lat={item.lat} lon={item.lon} routing={routing} onFind={onFindRoutes} onPick={onPickRoute} originCtl={originCtl} />
      <h3>History</h3>
      <ol className="timeline">
        {s.events.map((e) => (
          <li key={e.id}>
            <strong>{eventLabel(e)}</strong>
            <span>{e.authorName}{e.authorRole !== 'resident' ? ` (${e.authorRole})` : ''} · {when(e.timestampMs)}</span>
          </li>
        ))}
      </ol>
    </>
  );
}

export default function EventDetail({ item, onClose, routing, onFindRoutes, onPickRoute, originCtl, onAcknowledge }: DetailProps) {
  if (!item) return null;
  return (
    <aside className="detail" aria-label="Details">
      <div className="detail-head">
        <h2>{item.kind === 'sos' ? 'SOS request' : 'Flooded spot'}</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Close details">×</button>
      </div>
      <div className="detail-body">
        {item.kind === 'sos' ? (
          <SosDetail item={item} routing={routing} onFindRoutes={onFindRoutes} onPickRoute={onPickRoute} originCtl={originCtl} onAcknowledge={onAcknowledge} />
        ) : (
          <ReportDetail item={item} routing={routing} onFindRoutes={onFindRoutes} onPickRoute={onPickRoute} originCtl={originCtl} />
        )}
      </div>
    </aside>
  );
}
