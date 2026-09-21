'use client';

import { EVAC_LABEL, EvacState } from '@/lib/evac';
import { timeAgo } from '@/lib/items';

interface EvacListProps {
  states: EvacState[];
  selectedId: string | null;
  onSelect: (s: EvacState) => void;
}

export default function EvacList({ states, selectedId, onSelect }: EvacListProps) {
  return (
    <>
      <ul className="list">
        {states.map((s) => {
          const capacity = s.centre.capacityEstimate;
          const selected = s.centre.id === selectedId;
          return (
            <li key={s.centre.id}>
              <button className={`row ${selected ? 'is-selected' : ''}`} onClick={() => onSelect(s)} aria-pressed={selected}>
                <span className={`row-icon evac st-${s.status}`} aria-hidden="true">⌂</span>
                <span className="row-main">
                  <span className="row-title">{s.centre.name}</span>
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
      <p className="evac-note">Capacity figures are placeholders, not verified against any barangay record. Officials post status from their phones.</p>
    </>
  );
}
