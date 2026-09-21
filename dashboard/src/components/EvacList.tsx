'use client';

import { useEffect, useMemo, useState } from 'react';
import Combobox, { ComboOption } from './Combobox';
import { EVAC_KINDS, EVAC_LABEL, EvacState, KIND_LABEL } from '@/lib/evac';
import { timeAgo } from '@/lib/items';
import type { Psgc } from '@/lib/psgc';

/** Everything the Shelters tab needs to let an operator add, open and close shelters for a municipality. */
export interface EvacManage {
  /** The municipality the operator is acting for; they may change only its shelters. */
  acting: string;
  setActing: (municipality: string) => void;
  /** Municipality and barangay suggestions; null until the list has loaded. */
  psgc: Psgc | null;
  /** Sends a request to /api/evac. Rejects with a readable message. */
  write: (body: Record<string, unknown>) => Promise<void>;
  /** The map is waiting for a click to place a new shelter. */
  picking: boolean;
  onPick: () => void;
  /** The last point clicked on the map. */
  point: { lat: number; lon: number } | null;
}

interface EvacListProps {
  /** The shelters to list: those of the municipality being acted for. */
  states: EvacState[];
  /** Every shelter, for the municipality suggestions. */
  allStates: EvacState[];
  selectedId: string | null;
  onSelect: (s: EvacState) => void;
  manage: EvacManage;
}

function ActingFor({ manage, states }: { manage: EvacManage; states: EvacState[] }) {
  const { acting, setActing, psgc } = manage;
  // Municipalities that already have shelters come first (they are the ones an operator most likely wants),
  // then whatever the PSGC list finds for what is typed.
  const options = useMemo<ComboOption[]>(() => {
    const counts = new Map<string, number>();
    for (const s of states) counts.set(s.centre.municipality, (counts.get(s.centre.municipality) ?? 0) + 1);
    const q = acting.trim().toLowerCase();
    const inUse: ComboOption[] = [...counts]
      .filter(([m]) => !q || m.toLowerCase().includes(q))
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([m, n]) => ({ value: m, meta: `${n} shelter${n === 1 ? '' : 's'}` }));
    const listed = (psgc?.search(acting, 8) ?? []).filter((m) => !counts.has(m)).map((m) => ({ value: m }));
    return [...inUse, ...listed].slice(0, 10);
  }, [acting, psgc, states]);
  return (
    <div className="evac-acting">
      <label htmlFor="acting">Acting for municipality</label>
      <Combobox
        id="acting"
        value={acting}
        onChange={setActing}
        options={options}
        placeholder="Choose or type a municipality"
        hint={psgc ? 'Municipalities with shelters, or type to search every city and municipality' : 'Loading the list of places…'}
        emptyText="No match in the list. What you typed is used as it is."
      />
      <p className="hint">
        {acting ? 'You can add shelters here, and open or close the ones listed under this municipality.' : 'Choose a municipality to add or change shelters. Everyone can see them.'}
      </p>
    </div>
  );
}

function AddShelter({ manage }: { manage: EvacManage }) {
  const { acting, psgc, write, picking, onPick, point } = manage;
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [kind, setKind] = useState<string>('school');
  const [barangay, setBarangay] = useState('');
  const [capacity, setCapacity] = useState('');
  const [lat, setLat] = useState('');
  const [lon, setLon] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A click on the map fills in the coordinates.
  useEffect(() => {
    if (point) {
      setLat(point.lat.toFixed(6));
      setLon(point.lon.toFixed(6));
    }
  }, [point]);

  const la = Number(lat);
  const lo = Number(lon);
  const ready = name.trim() !== '' && lat.trim() !== '' && lon.trim() !== '' && Number.isFinite(la) && Number.isFinite(lo);
  const barangays = psgc?.searchBarangays(acting, barangay, 60) ?? [];

  if (!open) {
    return (
      <button className="btn btn-primary btn-block" onClick={() => setOpen(true)} disabled={!acting}>
        + Add a shelter{acting ? '' : ' (choose a municipality first)'}
      </button>
    );
  }

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await write({
        kind: 'centre',
        municipality: acting,
        name,
        shelterKind: kind,
        barangay,
        capacityEstimate: capacity.trim() === '' ? null : Number(capacity),
        lat: la,
        lon: lo,
      });
      setName('');
      setBarangay('');
      setCapacity('');
      setLat('');
      setLon('');
      setOpen(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the shelter');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="evac-form" onSubmit={(e) => { e.preventDefault(); if (ready) void submit(); }}>
      <h3>Add a shelter in {acting}</h3>
      <label>Name<input className="search" value={name} onChange={(e) => setName(e.target.value)} maxLength={120} required /></label>
      <label>Kind
        <select value={kind} onChange={(e) => setKind(e.target.value)}>
          {EVAC_KINDS.map((k) => <option key={k} value={k}>{KIND_LABEL[k]}</option>)}
        </select>
      </label>
      <div className="evac-field">
        <label htmlFor="barangay">Barangay</label>
        <Combobox
          id="barangay"
          value={barangay}
          onChange={setBarangay}
          options={barangays.map((b) => ({ value: b }))}
          placeholder={psgc?.find(acting) ? 'Choose or type a barangay' : 'Type a barangay'}
          hint={psgc?.find(acting) ? `Barangays of ${acting}` : undefined}
          emptyText="No match in the list. What you typed is used as it is."
          maxLength={120}
        />
      </div>
      <label>Capacity (estimate, optional)<input className="search" type="number" min={0} max={100000} value={capacity} onChange={(e) => setCapacity(e.target.value)} /></label>
      <div className="evac-coords">
        <label>Latitude<input className="search" inputMode="decimal" value={lat} onChange={(e) => setLat(e.target.value)} required /></label>
        <label>Longitude<input className="search" inputMode="decimal" value={lon} onChange={(e) => setLon(e.target.value)} required /></label>
      </div>
      <button type="button" className="btn" onClick={onPick} disabled={picking}>{picking ? 'Click the map… (Esc cancels)' : 'Pick location on the map'}</button>
      {error && <div className="banner-error" role="alert">{error}</div>}
      <div className="evac-form-actions">
        <button type="submit" className="btn btn-primary" disabled={!ready || busy}>{busy ? 'Saving…' : 'Save shelter'}</button>
        <button type="button" className="btn" onClick={() => setOpen(false)}>Cancel</button>
      </div>
      <p className="hint">A new shelter starts closed. Open it from its row once it is ready.</p>
    </form>
  );
}

export default function EvacList({ states, allStates, selectedId, onSelect, manage }: EvacListProps) {
  return (
    <>
      <div className="evac-manage">
        <ActingFor manage={manage} states={allStates} />
        <AddShelter manage={manage} />
      </div>
      {states.length === 0 && (
        <div className="list-empty">
          {manage.acting.trim()
            ? `No shelters in ${manage.acting.trim()} yet. Use “Add a shelter” above to add the first one.`
            : 'No shelters yet.'}
        </div>
      )}
      <ul className="list">
        {states.map((s) => {
          const capacity = s.centre.capacityEstimate;
          const selected = s.centre.id === selectedId;
          const where = [s.centre.barangay, s.centre.municipality].filter(Boolean).join(', ');
          return (
            <li key={s.centre.id}>
              <button className={`row ${selected ? 'is-selected' : ''}`} onClick={() => onSelect(s)} aria-pressed={selected}>
                <span className={`row-icon evac st-${s.status}`} aria-hidden="true">⌂</span>
                <span className="row-main">
                  <span className="row-title">{s.centre.name}</span>
                  <span className="row-where">{where}</span>
                  <span className="row-sub">
                    {s.occupancy != null ? `${s.occupancy}${capacity ? ` of ~${capacity}` : ''} inside` : capacity ? `capacity ~${capacity} (estimate)` : 'no figures'}
                    {s.updatedAtMs ? ` · ${s.updatedBy ?? 'an official'}, ${timeAgo(s.updatedAtMs)}` : ' · no update from an official'}
                  </span>
                </span>
                <span className={`pill pill-evac st-${s.status}`}>{EVAC_LABEL[s.status]}</span>
              </button>
            </li>
          );
        })}
      </ul>
      <p className="evac-note">Capacity figures are placeholders, not verified against any barangay record. Anyone with the dashboard PIN can change a municipality&apos;s shelters here; officials on a phone can only change their own.</p>
    </>
  );
}
