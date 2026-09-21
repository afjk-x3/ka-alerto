'use client';

import { Item, STATE_LABEL, SEVERITY_LABEL, reportCount, statusLine, timeAgo } from '@/lib/items';
import { whereShort } from '@/lib/gazetteer';

interface ItemListProps {
  items: Item[];
  selectedId: string | null;
  onSelect: (item: Item) => void;
  emptyText: string;
}

export default function ItemList({ items, selectedId, onSelect, emptyText }: ItemListProps) {
  if (items.length === 0) {
    return <div className="list-empty">{emptyText}</div>;
  }

  return (
    <ul className="list">
      {items.map((item) => {
        const selected = item.id === selectedId;
        if (item.kind === 'sos') {
          return (
            <li key={item.id}>
              <button
                className={`row row-sos ${item.closed ? 'is-closed' : ''} ${selected ? 'is-selected' : ''}`}
                onClick={() => onSelect(item)}
                aria-pressed={selected}
              >
                <span className={`row-icon sos ${item.closed ? 'closed' : ''}`} aria-hidden="true">{item.closed ? '✓' : 'SOS'}</span>
                <span className="row-main">
                  <span className="row-title">
                    {STATE_LABEL[item.state] ?? item.state}
                  </span>
                  <span className="row-where">{whereShort(item.lat, item.lon)}</span>
                  <span className="row-sub">
                    {item.context.people ? `${item.context.people} people · ` : ''}
                    {item.context.water ? `${item.context.water} · ` : ''}
                    {timeAgo(item.startedAtMs)}
                  </span>
                </span>
                {!item.closed && <span className="pill pill-danger">Open</span>}
              </button>
            </li>
          );
        }
        const s = item.summary;
        const n = reportCount(s);
        return (
          <li key={item.id}>
            <button
              className={`row ${item.stale ? 'is-stale' : ''} ${selected ? 'is-selected' : ''}`}
              onClick={() => onSelect(item)}
              aria-pressed={selected}
            >
              <span className={`row-icon sev-${item.stale ? 'none' : s.severity}`} aria-hidden="true">
                {item.stale ? '⏱' : s.severity}
              </span>
              <span className="row-main">
                <span className="row-title">{item.stale ? 'Expired — needs a fresh look' : SEVERITY_LABEL[s.severity] ?? s.severity}</span>
                <span className="row-where">{whereShort(item.lat, item.lon)}</span>
                <span className="row-sub">
                  {item.stale ? 'Last reports' : statusLine(s)} · {n} report{n === 1 ? '' : 's'}
                  {s.confirmCount > 0 ? ` · ${s.confirmCount} confirmed` : ''}
                  {s.disputeCount > 0 ? ` · ${s.disputeCount} disputed` : ''} · {timeAgo(s.lastEventMs)}
                </span>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
