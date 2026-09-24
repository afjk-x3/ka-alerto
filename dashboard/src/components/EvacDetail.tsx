'use client';

import { useState } from 'react';
import { EVAC_LABEL, EvacState, KIND_LABEL, canManage } from '@/lib/evac';
import type { EvacStatus } from '@/lib/evac';
import { timeAgo } from '@/lib/items';
import { describePlace, placeLine } from '@/lib/gazetteer';
import type { EvacManage } from './EvacList';

const STATUS_BUTTONS: { status: EvacStatus; label: string }[] = [
  { status: 'accepting', label: 'Open' },
  { status: 'nearly_full', label: 'Nearly full' },
  { status: 'full', label: 'Full' },
  { status: 'not_open', label: 'Close' },
];

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  if (children == null || children === '') return null;
  return (
    <div className="field">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

/** Open, nearly full and close, the head count, and removal for an added shelter. */
function Controls({ s, manage }: { s: EvacState; manage: EvacManage }) {
  const [occupancy, setOccupancy] = useState(s.occupancy == null ? '' : String(s.occupancy));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (body: Record<string, unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await manage.write(body);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="evac-controls">
      <h3>Update this shelter</h3>
      <label className="evac-occ-label">
        People inside now
        <input
          className="search evac-occ"
          type="number"
          min={0}
          max={100000}
          value={occupancy}
          onChange={(e) => setOccupancy(e.target.value)}
          placeholder="Not counted"
        />
      </label>
      <div className="evac-buttons">
        {STATUS_BUTTONS.filter((b) => b.status !== s.status).map((b) => (
          <button
            key={b.status}
            className={`btn ${b.status === 'accepting' ? 'btn-primary' : ''}`}
            disabled={busy}
            onClick={() => run({ kind: 'status', centreId: s.centre.id, status: b.status, occupancy: occupancy.trim() === '' ? null : Number(occupancy), municipality: s.centre.municipality })}
          >
            {b.label}
          </button>
        ))}
        {s.status !== 'not_open' && (
          <button
            className="btn"
            disabled={busy}
            onClick={() => run({ kind: 'status', centreId: s.centre.id, status: s.status, occupancy: occupancy.trim() === '' ? null : Number(occupancy), municipality: s.centre.municipality })}
          >
            Save head count
          </button>
        )}
      </div>
      {s.centre.custom && (
        <button
          className="evac-remove"
          disabled={busy}
          onClick={() => {
            if (window.confirm(`Remove "${s.centre.name}"? It disappears from every list; a phone that has not synced yet keeps showing it until it does.`)) {
              void run({
                kind: 'centre', removed: true, centreId: s.centre.id, municipality: s.centre.municipality, name: s.centre.name, shelterKind: s.centre.kind,
                lat: s.centre.lat, lon: s.centre.lon, barangay: s.centre.barangay, capacityEstimate: s.centre.capacityEstimate ?? null,
              });
            }
          }}
        >
          Remove this shelter
        </button>
      )}
      {error && <div className="banner-error" role="alert">{error}</div>}
    </section>
  );
}

/** The side panel for one shelter, like the one for a flooded spot: its details, and the controls for whoever may change it. */
export default function EvacDetail({ state, manage, onClose }: { state: EvacState | null; manage: EvacManage; onClose: () => void }) {
  if (!state) return null;
  const c = state.centre;
  const place = describePlace(c.lat, c.lon);
  const capacity = c.capacityEstimate;
  return (
    <aside className="detail" aria-label="Shelter details">
      <div className="detail-head">
        <h2>Shelter</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Close details">×</button>
      </div>
      <div className="detail-body">
        <div className={`status-banner evac-${state.status}`}>{c.name} · {EVAC_LABEL[state.status]}</div>
        <dl>
          <Field label="Where">{[c.barangay, c.municipality].filter(Boolean).join(', ')}</Field>
          <Field label="Kind">{KIND_LABEL[c.kind] ?? c.kind}</Field>
          <Field label="People inside">{state.occupancy != null ? `${state.occupancy}${capacity ? ` of ~${capacity}` : ''}` : undefined}</Field>
          <Field label="Capacity">{capacity ? `~${capacity} (estimate, not verified)` : undefined}</Field>
          <Field label="Last update">{state.updatedAtMs ? `${state.updatedBy ?? 'an official'}, ${timeAgo(state.updatedAtMs)}` : 'No update from an official yet'}</Field>
          <Field label="Location">
            {place && <span className="place">{placeLine(place)}<br /></span>}
            <span className="mono">{c.lat.toFixed(5)}, {c.lon.toFixed(5)}</span>
          </Field>
        </dl>
        {canManage(manage.acting, c) ? (
          <Controls key={`${c.id}:${state.status}:${state.occupancy}`} s={state} manage={manage} />
        ) : (
          <p className="hint">
            This shelter belongs to {c.municipality}. To change it, choose that municipality under “Acting for municipality”.
          </p>
        )}
      </div>
    </aside>
  );
}
