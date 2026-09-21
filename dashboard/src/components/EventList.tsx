'use client';

import { Item, STATE_LABEL, SEVERITY_LABEL, reportLabel, timeAgo } from '@/lib/items';

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
        const e = item.event;
        return (
          <li key={item.id}>
            <button
              className={`row ${item.stale ? 'is-stale' : ''} ${selected ? 'is-selected' : ''}`}
              onClick={() => onSelect(item)}
              aria-pressed={selected}
            >
              <span className={`row-icon sev-${e.severity ?? 'none'}`} aria-hidden="true">
                {e.severity ?? '·'}
              </span>
              <span className="row-main">
                <span className="row-title">
                  {e.type === 'flood_report' && e.severity
                    ? SEVERITY_LABEL[e.severity] ?? reportLabel(e.type)
                    : reportLabel(e.type)}
                </span>
                <span className="row-sub">
                  {e.authorName} · {timeAgo(e.timestampMs)}
                  {item.stale ? ' · expired' : ''}
                </span>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
