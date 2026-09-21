'use client';

import { useState, useCallback, useEffect, useMemo } from 'react';
import { useEvents } from '@/hooks/useEvents';
import { useAlerts } from '@/hooks/useAlerts';
import { getPin, setPin, clearPin } from '@/lib/api';
import { buildItems, sortItems, applyFilters, isFiltering, toCsv, NO_FILTERS, Filters, SEVERITY_LABEL, Item } from '@/lib/items';
import { NO_ROUTES, RoutingState, currentPosition, fetchRoutes, floodPoints, LatLon } from '@/lib/routing';
import PinGate from '@/components/PinGate';
import EventMap from '@/components/Map';
import ItemList from '@/components/EventList';
import EventDetail from '@/components/EventDetail';

type Tab = 'sos' | 'reports';

const POLL_MS = 5_000;

export default function DashboardPage() {
  const [locked, setLocked] = useState(false);
  const [wrongPin, setWrongPin] = useState(false);
  const [tab, setTab] = useState<Tab>('sos');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [routing, setRouting] = useState<RoutingState>(NO_ROUTES);
  const [origin, setOrigin] = useState<LatLon | null>(null);
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);

  const clearRoutes = useCallback(() => {
    setRouting(NO_ROUTES);
    setOrigin(null);
  }, []);

  const onAuthFail = useCallback(() => {
    setWrongPin(getPin() !== null);
    clearPin();
    setLocked(true);
  }, []);

  const { events, loading, error, updatedAt, refresh, reset } = useEvents(onAuthFail);

  const logout = useCallback(() => {
    clearPin();
    reset();
    setSelectedId(null);
    clearRoutes();
    setWrongPin(false);
    setLocked(true);
  }, [reset, clearRoutes]);

  // First load: works without a PIN when the server has none set, otherwise the 401 shows the gate.
  useEffect(() => {
    refresh();
  }, [refresh]);

  // Live updates: poll while unlocked, skip while the tab is hidden, catch up when it returns.
  useEffect(() => {
    if (locked) return;
    const tick = () => {
      if (!document.hidden) refresh({ silent: true });
    };
    const id = setInterval(tick, POLL_MS);
    document.addEventListener('visibilitychange', tick);
    return () => {
      clearInterval(id);
      document.removeEventListener('visibilitychange', tick);
    };
  }, [locked, refresh]);

  const items = useMemo(() => sortItems(buildItems(events)), [events]);
  const shown = useMemo(() => applyFilters(items, filters), [items, filters]);
  const sos = shown.filter((i) => i.kind === 'sos');
  const reports = shown.filter((i) => i.kind === 'report');
  const openSos = items.filter((i) => i.kind === 'sos' && !i.closed).length;
  const totalReports = items.filter((i) => i.kind === 'report').length;
  const selected = items.find((i) => i.id === selectedId) ?? null;
  const alerts = useAlerts(items, updatedAt !== null);

  // An unattended tab still shows that someone needs help.
  useEffect(() => {
    document.title = openSos > 0 ? `(${openSos}) SOS · KaAlerto LGU Dashboard` : 'KaAlerto LGU Dashboard';
  }, [openSos]);

  const select = useCallback((item: Item) => {
    clearRoutes();
    setSelectedId(item.id);
    setTab(item.kind === 'sos' ? 'sos' : 'reports');
  }, [clearRoutes]);

  const findRoutes = useCallback(async () => {
    if (!selected) return;
    setRouting({ status: 'locating', options: [], active: 0 });
    try {
      const from = await currentPosition();
      setOrigin(from);
      setRouting({ status: 'loading', options: [], active: 0 });
      const options = await fetchRoutes(from, { lat: selected.lat, lon: selected.lon }, floodPoints(items));
      setRouting({ status: 'done', options, active: 0 });
    } catch (e) {
      setOrigin(null);
      setRouting({ status: 'error', error: e instanceof Error ? e.message : 'Something went wrong', options: [], active: 0 });
    }
  }, [selected, items]);

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

  const exportCsv = () => {
    const url = URL.createObjectURL(new Blob(['﻿', toCsv(list)], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `kaalerto-${tab}-${new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-')}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="app">
      <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`} inert={collapsed}>
        <div className="sidebar-inner">
        <header className="sidebar-head">
          <div>
            <h1>KaAlerto</h1>
            <p>LGU response dashboard</p>
          </div>
          <div className="head-actions">
            <span className={`live ${error ? 'live-warn' : ''}`} role="status">
              <i className="live-dot" aria-hidden="true" />
              {error ? 'Reconnecting…' : loading ? 'Loading…' : 'Live'}
            </span>
            <button
              className="icon-btn"
              onClick={() => setCollapsed(true)}
              aria-label="Hide sidebar"
              aria-expanded={!collapsed}
              title="Hide sidebar"
            >
              «
            </button>
          </div>
        </header>

        <div className="alerts-row">
          {alerts.enabled ? (
            <button className="btn alerts-btn on" onClick={alerts.turnOff} title="Click to mute">
              {alerts.notifOk ? 'Alerts on: sound and notification for SOS and S3' : 'Alerts on: sound only (browser notifications are off)'}
            </button>
          ) : (
            <button className="btn alerts-btn off" onClick={alerts.turnOn}>
              Turn on alerts (sound and notification)
            </button>
          )}
        </div>

        {alerts.banner && (
          <div className="alert-banner" role="alert">
            <span>{alerts.banner.text} · {new Date(alerts.banner.at).toLocaleTimeString()}</span>
            <button onClick={alerts.dismiss} aria-label="Dismiss alert">×</button>
          </div>
        )}

        <div className="stats">
          <div className={`stat ${openSos > 0 ? 'stat-alert' : ''}`}>
            <b>{openSos}</b>
            <span>open SOS</span>
          </div>
          <div className="stat">
            <b>{totalReports}</b>
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

        <div className="filters">
          <select aria-label="Age" value={filters.ageMs} onChange={(e) => setFilters({ ...filters, ageMs: Number(e.target.value) })}>
            <option value={0}>Any age</option>
            <option value={3_600_000}>Last hour</option>
            <option value={6 * 3_600_000}>Last 6 hours</option>
            <option value={24 * 3_600_000}>Last 24 hours</option>
          </select>
          {tab === 'reports' ? (
            <select aria-label="Severity" value={filters.severity} onChange={(e) => setFilters({ ...filters, severity: e.target.value })}>
              <option value="">All severities</option>
              {Object.entries(SEVERITY_LABEL).map(([k, v]) => (
                <option key={k} value={k}>{k} · {v}</option>
              ))}
            </select>
          ) : (
            <select aria-label="SOS state" value={filters.sosState} onChange={(e) => setFilters({ ...filters, sosState: e.target.value as Filters['sosState'] })}>
              <option value="all">Open and closed</option>
              <option value="open">Open only</option>
              <option value="closed">Closed only</option>
            </select>
          )}
          <button className="btn" onClick={exportCsv} disabled={list.length === 0}>CSV</button>
          {isFiltering(filters) && <button className="btn" onClick={() => setFilters(NO_FILTERS)}>Clear</button>}
        </div>

        <ItemList
          items={list}
          selectedId={selectedId}
          onSelect={select}
          emptyText={
            loading
              ? 'Loading…'
              : isFiltering(filters)
                ? 'Nothing matches these filters.'
                : tab === 'sos'
                ? 'No SOS requests. New ones appear here automatically.'
                : 'No flood reports yet.'
          }
        />

        <footer className="sidebar-foot">
          <span>
            {updatedAt ? `Updated ${new Date(updatedAt).toLocaleTimeString()}` : 'Not loaded yet'} · checks every {POLL_MS / 1000} s
          </span>
          <button className="btn btn-block" onClick={logout}>
            Log out
          </button>
        </footer>
        </div>
      </aside>

      <main className="stage">
        {collapsed && (
          <button
            className="btn btn-map show-sidebar-btn"
            onClick={() => setCollapsed(false)}
            aria-label="Show sidebar"
            aria-expanded={false}
          >
            » Menu
          </button>
        )}
        <EventMap items={shown} selectedId={selectedId} onSelect={select} routes={routing.options} origin={origin} activeRoute={routing.active} />
        <EventDetail
          item={selected}
          onClose={() => {
            setSelectedId(null);
            clearRoutes();
          }}
          routing={routing}
          onFindRoutes={findRoutes}
          onPickRoute={(i) => setRouting((r) => ({ ...r, active: i }))}
        />
      </main>
    </div>
  );
}
