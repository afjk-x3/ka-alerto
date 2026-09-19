'use client';

import { Item, SosItem, ReportItem, STATE_LABEL, SEVERITY_LABEL, reportLabel, timeAgo } from '@/lib/items';

interface DetailProps {
  item: Item | null;
  onClose: () => void;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="field">
      <dt>{label}</dt>
      <dd>{children ?? '—'}</dd>
    </div>
  );
}

function Coords({ lat, lon }: { lat: number; lon: number }) {
  return (
    <>
      <span className="mono">{lat.toFixed(5)}, {lon.toFixed(5)}</span>
      {' · '}
      <a href={`https://www.google.com/maps/search/?api=1&query=${lat},${lon}`} target="_blank" rel="noreferrer">
        Open in Maps
      </a>
    </>
  );
}

const when = (ms: number) => new Date(ms).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });

function SosDetail({ item }: { item: SosItem }) {
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
        <Field label="Reached us via">{item.origin}{item.hopCount > 0 ? ` · ${item.hopCount} hop${item.hopCount > 1 ? 's' : ''}` : ''}</Field>
      </dl>
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

function ReportDetail({ item }: { item: ReportItem }) {
  const e = item.event;
  return (
    <>
      {e.severity && (
        <div className={`status-banner sev-${e.severity}`}>
          {e.severity} · {SEVERITY_LABEL[e.severity] ?? e.severity}
        </div>
      )}
      <dl>
        <Field label="Water level">{e.waterLevel}</Field>
        <Field label="Location"><Coords lat={e.lat} lon={e.lon} /></Field>
        <Field label="Reported by">{e.authorName} ({e.authorRole})</Field>
        <Field label="Reported">{when(e.timestampMs)} ({timeAgo(e.timestampMs)})</Field>
        <Field label="Status">{item.stale ? 'Expired — needs a fresh look' : 'Current'}</Field>
        <Field label="Note">{e.note || undefined}</Field>
        {e.disputeReason && <Field label="Dispute reason">{e.disputeReason}</Field>}
        <Field label="Reached us via">{e.origin}{e.hopCount > 0 ? ` · ${e.hopCount} hop${e.hopCount > 1 ? 's' : ''}` : ''}</Field>
      </dl>
    </>
  );
}

export default function EventDetail({ item, onClose }: DetailProps) {
  if (!item) return null;
  return (
    <aside className="detail" aria-label="Details">
      <div className="detail-head">
        <h2>{item.kind === 'sos' ? 'SOS request' : reportLabel(item.event.type)}</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Close details">×</button>
      </div>
      <div className="detail-body">
        {item.kind === 'sos' ? <SosDetail item={item} /> : <ReportDetail item={item} />}
      </div>
    </aside>
  );
}
