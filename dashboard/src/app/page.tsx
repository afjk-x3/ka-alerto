'use client';

import { useState, useCallback, useEffect, useMemo } from 'react';
import { useEvents } from '@/hooks/useEvents';
import { getPin, setPin, clearPin } from '@/lib/api';
import { buildItems, sortItems, Item } from '@/lib/items';
import PinGate from '@/components/PinGate';
import EventMap from '@/components/Map';
import ItemList from '@/components/EventList';
import EventDetail from '@/components/EventDetail';

type Tab = 'sos' | 'reports';

export default function DashboardPage() {
  const [locked, setLocked] = useState(false);
  const [wrongPin, setWrongPin] = useState(false);
  const [tab, setTab] = useState<Tab>('sos');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const onAuthFail = useCallback(() => {
    setWrongPin(getPin() !== null);
    clearPin();
    setLocked(true);
  }, []);

  const { events, loading, error, updatedAt, refresh } = useEvents(onAuthFail);

  // First load: works without a PIN when the server has none set, otherwise the 401 shows the gate.
  useEffect(() => {
    refresh();
  }, [refresh]);

  const items = useMemo(() => sortItems(buildItems(events)), [events]);
  const sos = items.filter((i) => i.kind === 'sos');
  const reports = items.filter((i) => i.kind === 'report');
  const openSos = sos.filter((i) => i.kind === 'sos' && !i.closed).length;
  const selected = items.find((i) => i.id === selectedId) ?? null;

  const select = useCallback((item: Item) => {
    setSelectedId(item.id);
    setTab(item.kind === 'sos' ? 'sos' : 'reports');
  }, []);

  if (locked) {
    return (
      <PinGate
        wrongPin={wrongPin}
        onSubmit={(pin) => {
          setPin(pin);
          setLocked(false);
          refresh();
        }}
      />
    );
  }

  const list = tab === 'sos' ? sos : reports;

  return (
    <div className="app">
      <aside className="sidebar">
        <header className="sidebar-head">
          <div>
            <h1>KaAlerto</h1>
            <p>LGU response dashboard</p>
          </div>
          <button className="btn btn-primary" onClick={refresh} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </header>

        <div className="stats">
          <div className={`stat ${openSos > 0 ? 'stat-alert' : ''}`}>
            <b>{openSos}</b>
            <span>open SOS</span>
          </div>
          <div className="stat">
            <b>{reports.length}</b>
            <span>reports</span>
          </div>
        </div>

        {error && <div className="banner-error" role="alert">{error}</div>}

        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={tab === 'sos'} className={tab === 'sos' ? 'active' : ''} onClick={() => setTab('sos')}>
            SOS requests <span className="count">{sos.length}</span>
          </button>
          <button role="tab" aria-selected={tab === 'reports'} className={tab === 'reports' ? 'active' : ''} onClick={() => setTab('reports')}>
            Flood reports <span className="count">{reports.length}</span>
          </button>
        </div>

        <ItemList
          items={list}
          selectedId={selectedId}
          onSelect={select}
          emptyText={
            loading
              ? 'Loading…'
              : tab === 'sos'
                ? 'No SOS requests. Anything sent from a phone will appear here after a refresh.'
                : 'No flood reports yet.'
          }
        />

        <footer className="sidebar-foot">
          {updatedAt ? `Updated ${new Date(updatedAt).toLocaleTimeString()}` : 'Not loaded yet'} · refresh to see new activity
        </footer>
      </aside>

      <main className="stage">
        <EventMap items={items} selectedId={selectedId} onSelect={select} />
        <EventDetail item={selected} onClose={() => setSelectedId(null)} />
      </main>
    </div>
  );
}
