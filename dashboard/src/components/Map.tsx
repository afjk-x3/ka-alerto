'use client';

import { useEffect, useRef } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { Item } from '@/lib/items';

// Demo area centre (DemoArea.kt), used only until there is something to fit to.
const DEMO_CENTER: [number, number] = [120.6058, 18.1709];

interface MapProps {
  items: Item[];
  selectedId: string | null;
  onSelect: (item: Item) => void;
}

function markerClass(item: Item): string {
  if (item.kind === 'sos') return `mk mk-sos ${item.closed ? 'is-closed' : ''}`;
  return `mk sev-${item.event.severity ?? 'none'} ${item.stale ? 'is-stale' : ''}`;
}

export default function EventMap({ items, selectedId, onSelect }: MapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markers = useRef(new Map<string, { marker: maplibregl.Marker; el: HTMLElement }>());
  const fitted = useRef(false);
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

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
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
      markers.current.clear();
      fitted.current = false;
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

    markers.current.forEach(({ marker }) => marker.remove());
    markers.current.clear();

    for (const item of items) {
      const el = document.createElement('button');
      el.type = 'button';
      el.className = markerClass(item);
      el.setAttribute('aria-label', item.kind === 'sos' ? 'SOS request' : 'Flood report');
      el.innerHTML = '<span class="mk-dot"></span>';
      el.style.zIndex = item.kind === 'sos' ? '2' : '1';
      el.addEventListener('click', (ev) => {
        ev.stopPropagation();
        onSelectRef.current(item);
      });
      const marker = new maplibregl.Marker({ element: el }).setLngLat([item.lon, item.lat]).addTo(map);
      markers.current.set(item.id, { marker, el });
    }

    if (!fitted.current && items.length > 0) {
      fitted.current = true;
      fitAll(items);
    }
  }, [items]);

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
      <div ref={containerRef} className="map" />
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
