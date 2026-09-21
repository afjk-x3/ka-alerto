'use client';

import { useEffect, useRef } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { Item } from '@/lib/items';
import { LatLon, RouteOption } from '@/lib/routing';
import { EVAC_LABEL, EvacState } from '@/lib/evac';

// Demo area centre (DemoArea.kt), used only until there is something to fit to.
const DEMO_CENTER: [number, number] = [120.6058, 18.1709];

interface MapProps {
  items: Item[];
  selectedId: string | null;
  onSelect: (item: Item) => void;
  /** Route alternatives to draw, where the viewer is, and which alternative is highlighted. */
  routes: RouteOption[];
  origin: LatLon | null;
  activeRoute: number;
  /** Evacuation centres to pin, and the one the list has selected (the map flies to it). */
  centres: EvacState[];
  selectedCentreId: string | null;
}

function markerClass(item: Item): string {
  if (item.kind === 'sos') return `mk mk-sos ${item.closed ? 'is-closed' : ''}`;
  return `mk sev-${item.event.severity ?? 'none'} ${item.stale ? 'is-stale' : ''}`;
}

export default function EventMap({ items, selectedId, onSelect, routes, origin, activeRoute, centres, selectedCentreId }: MapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markers = useRef(new Map<string, { marker: maplibregl.Marker; el: HTMLElement }>());
  const fitted = useRef(false);
  const itemsById = useRef(new Map<string, Item>());
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;
  const selectedRef = useRef(selectedId);
  selectedRef.current = selectedId;
  const originMarker = useRef<maplibregl.Marker | null>(null);
  // The style is ready once the map has fired 'load'. isStyleLoaded() is NOT a substitute: it is
  // false whenever any tile is in flight, and 'load' fires only once, so waiting on it can hang.
  const styleReady = useRef(false);
  const drawRoutes = useRef<() => void>(() => {});

  useEffect(() => {
    if (!containerRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: 'https://tiles.openfreemap.org/styles/liberty',
      center: DEMO_CENTER,
      zoom: 13,
    });
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-right');
    map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');
    // The Liberty style names a few icons its sprite lacks (gate, office, ...). Give them a blank
    // pixel instead of logging an error for each.
    map.on('styleimagemissing', (e) => {
      if (!map.hasImage(e.id)) map.addImage(e.id, { width: 1, height: 1, data: new Uint8Array(4) });
    });
    mapRef.current = map;
    map.on('load', () => {
      styleReady.current = true;
      drawRoutes.current();
    });
    // The sidebar collapsing changes the container's width without a window resize.
    const observer = new ResizeObserver(() => map.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      map.remove();
      mapRef.current = null;
      markers.current.clear();
      fitted.current = false;
      styleReady.current = false;
    };
  }, []);

  const fitAll = (list: Item[]) => {
    const map = mapRef.current;
    if (!map || list.length === 0) return;
    const bounds = new maplibregl.LngLatBounds();
    list.forEach((i) => bounds.extend([i.lon, i.lat]));
    map.fitBounds(bounds, { padding: 80, maxZoom: 15, duration: 600 });
  };

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    // Diff against the markers already on the map: a poll that changes nothing touches nothing.
    itemsById.current = new Map(items.map((i) => [i.id, i]));
    markers.current.forEach(({ marker }, id) => {
      if (!itemsById.current.has(id)) {
        marker.remove();
        markers.current.delete(id);
      }
    });

    for (const item of items) {
      const label = item.kind === 'sos' ? (item.closed ? 'SOS request, closed' : 'SOS request, open') : `Flood report, ${item.event.severity ?? 'no severity'}`;
      const existing = markers.current.get(item.id);
      if (existing) {
        existing.el.className = markerClass(item);
        existing.el.classList.toggle('is-selected', item.id === selectedRef.current);
        existing.el.setAttribute('aria-label', label);
        existing.marker.setLngLat([item.lon, item.lat]);
        continue;
      }
      const el = document.createElement('button');
      el.type = 'button';
      el.className = markerClass(item);
      el.innerHTML = '<span class="mk-dot"></span>';
      el.style.zIndex = item.kind === 'sos' ? '2' : '1';
      el.classList.toggle('is-selected', item.id === selectedRef.current);
      el.addEventListener('click', (ev) => {
        ev.stopPropagation();
        const latest = itemsById.current.get(item.id);
        if (latest) onSelectRef.current(latest);
      });
      const marker = new maplibregl.Marker({ element: el }).setLngLat([item.lon, item.lat]).addTo(map);
      // MapLibre stamps its own generic "Map marker" label when the marker is added, so ours goes after.
      el.setAttribute('aria-label', label);
      markers.current.set(item.id, { marker, el });
    }

    if (!fitted.current && items.length > 0) {
      fitted.current = true;
      fitAll(items);
    }
  }, [items]);

  // Evacuation centres: square pins, coloured by status. Rebuilt on every poll like the report markers.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const pins = centres.map((s) => {
      const el = document.createElement('div');
      el.className = `mk-evac st-${s.status}`;
      el.title = `${s.centre.name}: ${EVAC_LABEL[s.status]}`;
      const pin = new maplibregl.Marker({ element: el }).setLngLat([s.centre.lon, s.centre.lat]).addTo(map);
      el.setAttribute('aria-label', `Shelter: ${s.centre.name}, ${EVAC_LABEL[s.status]}`);
      return pin;
    });
    return () => pins.forEach((m) => m.remove());
  }, [centres]);

  useEffect(() => {
    const c = centres.find((s) => s.centre.id === selectedCentreId);
    if (c && mapRef.current) mapRef.current.flyTo({ center: [c.centre.lon, c.centre.lat], zoom: Math.max(mapRef.current.getZoom(), 15), duration: 700 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCentreId]);

  // Route lines. Redrawn when the alternatives or the highlighted one change.
  drawRoutes.current = () => {
    const map = mapRef.current;
    if (!map || !styleReady.current) return;
    const features = routes
      .map((r, i) => ({
        type: 'Feature' as const,
        properties: { active: i === activeRoute, safest: r.safest },
        geometry: { type: 'LineString' as const, coordinates: r.coords },
      }))
      .sort((x, y) => Number(x.properties.active) - Number(y.properties.active)); // highlighted on top
    const data = { type: 'FeatureCollection' as const, features };
    const src = map.getSource('routes') as maplibregl.GeoJSONSource | undefined;
    if (src) {
      src.setData(data);
      return;
    }
    map.addSource('routes', { type: 'geojson', data });
    map.addLayer({
      id: 'routes-casing', type: 'line', source: 'routes',
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#ffffff', 'line-width': ['case', ['get', 'active'], 9, 7] },
    });
    map.addLayer({
      id: 'routes-line', type: 'line', source: 'routes',
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: {
        'line-color': ['case', ['get', 'safest'], '#1f9d55', '#5a6677'],
        'line-width': ['case', ['get', 'active'], 5, 3.5],
        'line-opacity': ['case', ['get', 'active'], 1, 0.6],
      },
    });
  };
  useEffect(() => {
    drawRoutes.current();
  }, [routes, activeRoute]);

  // "You are here" marker, and frame the whole trip when new routes arrive.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    originMarker.current?.remove();
    originMarker.current = null;
    if (!origin) return;
    const el = document.createElement('div');
    el.className = 'origin-dot';
    el.title = 'Your location';
    originMarker.current = new maplibregl.Marker({ element: el }).setLngLat([origin.lon, origin.lat]).addTo(map);
  }, [origin]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || routes.length === 0) return;
    const bounds = new maplibregl.LngLatBounds();
    routes.forEach((r) => r.coords.forEach((c) => bounds.extend(c)));
    map.fitBounds(bounds, { padding: { top: 80, bottom: 80, left: 80, right: 400 }, maxZoom: 16, duration: 700 });
  }, [routes]);

  useEffect(() => {
    markers.current.forEach(({ el }, id) => el.classList.toggle('is-selected', id === selectedId));
    const item = items.find((i) => i.id === selectedId);
    if (item && mapRef.current) {
      mapRef.current.flyTo({ center: [item.lon, item.lat], zoom: Math.max(mapRef.current.getZoom(), 15), duration: 700 });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  return (
    <div className="map-wrap">
      <div ref={containerRef} className="map" role="region" aria-label="Map of SOS requests, flood reports and shelters" />
      <button className="btn btn-map fit-btn" onClick={() => fitAll(items)} disabled={items.length === 0}>
        Show all
      </button>
      <div className="legend" aria-label="Map legend">
        <span><i className="lg sev-S3" />Impassable</span>
        <span><i className="lg sev-S2" />Cars can&apos;t pass</span>
        <span><i className="lg sev-S1" />Caution</span>
        <span><i className="lg sev-S0" />Cleared</span>
        <span><i className="lg sev-SX" />Conflicting</span>
        <span><i className="lg lg-sos" />SOS</span>
      </div>
    </div>
  );
}
