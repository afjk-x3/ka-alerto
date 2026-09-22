'use client';

import { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import { useEvents } from '@/hooks/useEvents';
import { useAlerts } from '@/hooks/useAlerts';
import { getPin, setPin, clearPin, postEvac } from '@/lib/api';
import { buildItems, sortItems, applyFilters, isFiltering, toCsv, NO_FILTERS, Filters, SEVERITY_LABEL, Item } from '@/lib/items';
import { NO_ROUTES, NO_ORIGIN, OriginMode, RoutingState, currentPosition, fetchRoutes, floodPoints, LatLon, originOf } from '@/lib/routing';
import PinGate from '@/components/PinGate';
import Logo from '@/components/Logo';
import EventMap from '@/components/Map';
import ItemList from '@/components/EventList';
import EvacList, { EvacManage } from '@/components/EvacList';
import EvacDetail from '@/components/EvacDetail';
import { buildEvacStates, DEMO_MUNICIPALITY, sameMunicipality } from '@/lib/evac';
import { makePsgc, Psgc } from '@/lib/psgc';
import EventDetail, { OriginControl } from '@/components/EventDetail';

type Tab = 'sos' | 'reports' | 'evac';
const TABS: { key: Tab; label: string }[] = [
  { key: 'sos', label: 'SOS requests' },
  { key: 'reports', label: 'Flood reports' },
  { key: 'evac', label: 'Shelters' },
];

const POLL_MS = 5_000;
const STATION_KEY = 'kaalerto_station';
const ACTING_KEY = 'kaalerto_acting';

export default function DashboardPage() {
  const [locked, setLocked] = useState(false);
  const [wrongPin, setWrongPin] = useState(false);
  const [tab, setTab] = useState<Tab>('sos');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [routing, setRouting] = useState<RoutingState>(NO_ROUTES);
  const [origin, setOrigin] = useState<LatLon | null>(null);
  // Where routes start: this computer, a saved station, or a click on the map.
  const [originState, setOriginState] = useState(NO_ORIGIN);
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [selectedCentreId, setSelectedCentreId] = useState<string | null>(null);
  // Shelters: the municipality the operator acts for, the municipality/barangay list, and a map click for a new shelter.
  const [acting, setActingState] = useState(DEMO_MUNICIPALITY);
  const [psgc, setPsgc] = useState<Psgc | null>(null);
  const [shelterPicking, setShelterPicking] = useState(false);
  const [shelterPoint, setShelterPoint] = useState<{ lat: number; lon: number } | null>(null);
  const tabRefs = useRef<Record<Tab, HTMLButtonElement | null>>({ sos: null, reports: null, evac: null });

  const clearRoutes = useCallback(() => {
    setRouting(NO_ROUTES);
    setOrigin(null);
  }, []);

  const onAuthFail = useCallback(() => {
    setWrongPin(getPin() !== null);
    clearPin();
    setLocked(true);
  }, []);

  const { events, loading, error, updatedAt, truncated, checking, refresh, reset } = useEvents(onAuthFail);

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

  // The saved station survives a reload; per browser, since the dashboard has no accounts.
  useEffect(() => {
    try {
      const raw = localStorage.getItem(STATION_KEY);
      if (raw) setOriginState((o) => ({ ...o, station: JSON.parse(raw) as LatLon }));
    } catch {
      // Storage can be blocked; the station simply is not remembered.
    }
  }, []);

  // The municipality being acted for survives a reload; the 0.6 MB place list loads only when the tab opens.
  useEffect(() => {
    try {
      // An operator who cleared the box on purpose (an empty saved value) keeps it empty.
      const saved = localStorage.getItem(ACTING_KEY);
      if (saved !== null) setActingState(saved);
    } catch {
      // Storage can be blocked; the choice is simply not remembered.
    }
  }, []);
  useEffect(() => {
    if (tab !== 'evac' || psgc) return;
    void import('@/data/psgc.json').then((m) => setPsgc(makePsgc(m.default as never)));
  }, [tab, psgc]);
  const setActing = useCallback((municipality: string) => {
    setActingState(municipality);
    try {
      localStorage.setItem(ACTING_KEY, municipality);
    } catch {
      // Not remembered across reloads.
    }
  }, []);
  // Esc stops waiting for a shelter location, and otherwise closes the shelter panel.
  useEffect(() => {
    if (!shelterPicking && !selectedCentreId) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (shelterPicking) setShelterPicking(false);
      else setSelectedCentreId(null);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [shelterPicking, selectedCentreId]);

  // Escape stops picking a start first, then closes the detail panel.
  useEffect(() => {
    if (!selectedId) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (originState.picking) {
        setOriginState((o) => ({ ...o, picking: false }));
        return;
      }
      setSelectedId(null);
      clearRoutes();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [selectedId, clearRoutes, originState.picking]);

  // Live updates: poll while unlocked, hidden tab included. The alarm exists for the operator who is
  // looking at something else, so skipping hidden tabs would defeat it. Browsers already throttle a
  // background tab's timer (about once a minute), so an alert there is late, never lost. Coming back
  // to the tab refreshes at once.
  useEffect(() => {
    if (locked) return;
    const tick = () => refresh({ silent: true });
    const id = setInterval(tick, POLL_MS);
    document.addEventListener('visibilitychange', tick);
    return () => {
      clearInterval(id);
      document.removeEventListener('visibilitychange', tick);
    };
  }, [locked, refresh]);

  const items = useMemo(() => sortItems(buildItems(events)), [events]);
  const centres = useMemo(() => buildEvacStates(events), [events]);
  // The Shelters tab, its badge and its map pins show the municipality being acted for; a municipality with
  // no shelters yet is simply an empty list. With none chosen, every shelter is shown.
  const shownCentres = useMemo(() => (acting.trim() ? centres.filter((c) => sameMunicipality(acting, c.centre.municipality)) : centres), [centres, acting]);
  const selectedCentre = tab === 'evac' ? (shownCentres.find((c) => c.centre.id === selectedCentreId) ?? null) : null;
  const openCentres = shownCentres.filter((c) => c.status !== 'not_open').length;
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
    setSelectedCentreId(null);
    setOriginState((o) => ({ ...o, picking: false }));
    setSelectedId(item.id);
    setTab(item.kind === 'sos' ? 'sos' : 'reports');
  }, [clearRoutes]);

  const findRoutes = useCallback(async () => {
    if (!selected) return;
    setRouting({ status: 'locating', options: [], active: 0 });
    try {
      const from = originOf(originState) ?? (await currentPosition());
      setOrigin(from);
      setRouting({ status: 'loading', options: [], active: 0 });
      const options = await fetchRoutes(from, { lat: selected.lat, lon: selected.lon }, floodPoints(items));
      setRouting({ status: 'done', options, active: 0 });
    } catch (e) {
      setOrigin(null);
      setRouting({ status: 'error', error: e instanceof Error ? e.message : 'Something went wrong', options: [], active: 0 });
    }
  }, [selected, items, originState]);

  const originCtl: OriginControl = {
    state: originState,
    setMode: (mode: OriginMode) => {
      clearRoutes();
      setOriginState((o) => ({ ...o, mode, picking: mode === 'pick' && o.picked === null }));
    },
    saveStation: () => {
      const p = originState.picked;
      if (!p) return;
      setOriginState((o) => ({ ...o, station: p }));
      try {
        localStorage.setItem(STATION_KEY, JSON.stringify(p));
      } catch {
        // Not remembered across reloads; still used for this session.
      }
    },
    repick: () => {
      clearRoutes();
      setOriginState((o) => ({ ...o, picked: null, picking: true }));
    },
  };

  const manage: EvacManage = {
    acting,
    setActing,
    psgc,
    write: async (body) => {
      try {
        await postEvac(body);
      } catch (e) {
        if (e instanceof Error && e.name === 'AuthError') onAuthFail();
        throw e;
      }
      void refresh({ silent: true });
    },
    picking: shelterPicking,
    onPick: () => setShelterPicking(true),
    point: shelterPoint,
  };

  // Nothing renders until the very first refresh settles — otherwise a locked dashboard's own
  // empty shell (sidebar, "Loading…", zero counts) flashes for a frame before the PIN gate.
  if (checking) {
    return (
      <div className="gate">
        <Logo size={56} />
      </div>
    );
  }

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
            <span>flooded spots</span>
          </div>
        </div>

        {error && <div className="banner-error" role="alert">{error}</div>}
        {truncated && (
          <div className="banner-note" role="status">
            Showing the newest 20,000 events only — older ones are hidden.
          </div>
        )}

        <div className="tabs" role="tablist" aria-label="Lists">
          {TABS.map(({ key, label }) => (
            <button
              key={key}
              ref={(el) => { tabRefs.current[key] = el; }}
              id={`tab-${key}`}
              role="tab"
              aria-selected={tab === key}
              aria-controls="tabpanel"
              tabIndex={tab === key ? 0 : -1}
              className={tab === key ? 'active' : ''}
              onClick={() => setTab(key)}
              onKeyDown={(e) => {
                const i = TABS.findIndex((t) => t.key === key);
                const next = e.key === 'ArrowRight' ? i + 1 : e.key === 'ArrowLeft' ? i - 1 : e.key === 'Home' ? 0 : e.key === 'End' ? TABS.length - 1 : null;
                if (next === null) return;
                e.preventDefault();
                const target = TABS[(next + TABS.length) % TABS.length].key;
                setTab(target);
                tabRefs.current[target]?.focus();
              }}
            >
              {label}{' '}
              <span className="count" title={key === 'evac' ? `${openCentres} of ${shownCentres.length} shelters open` : undefined}>
                {key === 'sos' ? sos.length : key === 'reports' ? reports.length : `${openCentres}/${shownCentres.length}`}
              </span>
            </button>
          ))}
        </div>

        <div role="tabpanel" id="tabpanel" aria-labelledby={`tab-${tab}`} className="tabpanel">
        {tab !== 'evac' && (
        <div className="filters">
          <input
            type="search"
            className="search"
            aria-label="Search"
            placeholder="Search place, name, status…"
            value={filters.query}
            onChange={(e) => setFilters({ ...filters, query: e.target.value })}
          />
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
              <option value="all">Any state</option>
              <option value="open">Open only</option>
              <option value="closed">Closed only</option>
            </select>
          )}
          <button className="btn" onClick={exportCsv} disabled={list.length === 0}>CSV</button>
          {isFiltering(filters) && <button className="btn" onClick={() => setFilters(NO_FILTERS)}>Clear</button>}
        </div>
        )}

        {tab === 'evac' ? (
          <EvacList
            states={shownCentres}
            allStates={centres}
            selectedId={selectedCentreId}
            onSelect={(s) => {
              setSelectedCentreId(s.centre.id);
              setSelectedId(null);
              clearRoutes();
            }}
            manage={manage}
          />
        ) : (
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
        )}
        </div>

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
        <EventMap
          centres={shownCentres}
          selectedCentreId={tab === 'evac' ? selectedCentreId : null}
          items={shown}
          selectedId={selectedId}
          onSelect={select}
          routes={routing.options}
          origin={origin ?? (selected ? originOf(originState) : null)}
          activeRoute={routing.active}
          picking={(originState.picking && selected !== null) || shelterPicking}
          onPickOrigin={(at) => {
            if (shelterPicking) {
              setShelterPoint(at);
              setShelterPicking(false);
              return;
            }
            clearRoutes();
            setOriginState((o) => ({ ...o, picked: at, picking: false }));
          }}
        />
        <EvacDetail state={selectedCentre} manage={manage} onClose={() => setSelectedCentreId(null)} />
        <EventDetail
          item={selected}
          onClose={() => {
            setSelectedId(null);
            clearRoutes();
          }}
          routing={routing}
          originCtl={originCtl}
          onFindRoutes={findRoutes}
          onPickRoute={(i) => setRouting((r) => ({ ...r, active: i }))}
        />
      </main>
    </div>
  );
}
