'use client';

import { useEffect, useState } from 'react';
import { fetchPhoto } from '@/lib/api';
import { RoutingState, describeRoute, googleDirectionsUrl } from '@/lib/routing';
import { Item, SosItem, ReportItem, STATE_LABEL, SEVERITY_LABEL, reportLabel, timeAgo } from '@/lib/items';

interface DetailProps {
  item: Item | null;
  onClose: () => void;
  routing: RoutingState;
  onFindRoutes: () => void;
  onPickRoute: (i: number) => void;
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

function Coords({ lat, lon }: { lat: number; lon: number }) {
  return <span className="mono">{lat.toFixed(5)}, {lon.toFixed(5)}</span>;
}

/** Directions from wherever the viewer is: a Google Maps handoff, plus routes ranked against current flood reports. */
function Directions({ lat, lon, routing, onFind, onPick }: { lat: number; lon: number; routing: RoutingState; onFind: () => void; onPick: (i: number) => void }) {
  const busy = routing.status === 'locating' || routing.status === 'loading';
  return (
    <section className="directions">
      <h3>Directions</h3>
      <div className="dir-actions">
        <a className="btn btn-primary dir-link" href={googleDirectionsUrl({ lat, lon })} target="_blank" rel="noreferrer">
          Directions in Google Maps
        </a>
        <button className="btn" onClick={onFind} disabled={busy}>
          {routing.status === 'locating' ? 'Finding you…' : routing.status === 'loading' ? 'Finding routes…' : 'Show routes ranked by reported floods'}
        </button>
      </div>
      <p className="hint dir-hint">Google Maps starts from your current location and lists its own alternatives.</p>
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

type DirProps = Pick<DetailProps, 'routing' | 'onFindRoutes' | 'onPickRoute'>;

function SosDetail({ item, routing, onFindRoutes, onPickRoute }: { item: SosItem } & DirProps) {
  const c = item.context;
  return (
    <>
      <div className={`status-banner ${item.closed ? 'closed' : 'open'}`}>
        {STATE_LABEL[item.state] ?? item.state}
      </div>
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
      <Directions lat={item.lat} lon={item.lon} routing={routing} onFind={onFindRoutes} onPick={onPickRoute} />
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

function ReportDetail({ item, routing, onFindRoutes, onPickRoute }: { item: ReportItem } & DirProps) {
  const e = item.event;
  return (
    <>
      {e.severity && (
        <div className={`status-banner sev-${e.severity}`}>
          {e.severity} · {SEVERITY_LABEL[e.severity] ?? e.severity}
        </div>
      )}
      {item.photoHash && <Photo hash={item.photoHash} />}
      <dl>
        <Field label="Water level">{e.waterLevel}</Field>
        <Field label="Location"><Coords lat={e.lat} lon={e.lon} /></Field>
        <Field label="Reported by">{e.authorName} ({e.authorRole})</Field>
        <Field label="Reported">{when(e.timestampMs)} ({timeAgo(e.timestampMs)})</Field>
        <Field label="Status">{item.stale ? 'Expired — needs a fresh look' : 'Current'}</Field>
        <Field label="Note">{e.note || undefined}</Field>
        {e.disputeReason && <Field label="Dispute reason">{e.disputeReason}</Field>}
      </dl>
      <Directions lat={item.lat} lon={item.lon} routing={routing} onFind={onFindRoutes} onPick={onPickRoute} />
    </>
  );
}

export default function EventDetail({ item, onClose, routing, onFindRoutes, onPickRoute }: DetailProps) {
  if (!item) return null;
  return (
    <aside className="detail" aria-label="Details">
      <div className="detail-head">
        <h2>{item.kind === 'sos' ? 'SOS request' : reportLabel(item.event.type)}</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Close details">×</button>
      </div>
      <div className="detail-body">
        {item.kind === 'sos' ? (
          <SosDetail item={item} routing={routing} onFindRoutes={onFindRoutes} onPickRoute={onPickRoute} />
        ) : (
          <ReportDetail item={item} routing={routing} onFindRoutes={onFindRoutes} onPickRoute={onPickRoute} />
        )}
      </div>
    </aside>
  );
}
