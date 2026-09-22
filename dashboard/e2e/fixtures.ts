import { Page } from '@playwright/test';
import type { Event } from '../src/lib/types';

const now = Date.now();

export const SOS_EVENT: Event = {
  id: 'sos-e2e-1', type: 'sos', lat: 15.9101, lon: 120.4611, featureRef: null,
  severity: null, waterLevel: null, authorId: 'resident-1', authorName: 'Juan D.', authorRole: 'resident',
  timestampMs: now - 60_000, expiresAt: now + 12 * 3_600_000, origin: 'server', hopCount: 0,
  note: null, disputeReason: null, payload: JSON.stringify({ sosId: 'sos-e2e-1', accuracyMeters: 12 }),
};

export const FLOOD_REPORT_EVENT: Event = {
  id: 'report-e2e-1', type: 'flood_report', lat: 15.9110, lon: 120.4620, featureRef: 'geo-e2e-1',
  severity: 'S2', waterLevel: 'KNEE', authorId: 'resident-2', authorName: 'Maria S.', authorRole: 'resident',
  timestampMs: now - 120_000, expiresAt: now + 6 * 3_600_000, origin: 'mesh', hopCount: 1,
  note: null, disputeReason: null, payload: null,
};

/**
 * Fulfils every call the dashboard makes to Supabase, entirely in the browser (page.route),
 * so these tests never reach the real table. GET /api/events serves whatever is in `events`
 * (mimicking the real 401-until-a-PIN-header rule); POST /api/sos and /api/evac push a new
 * event into that same array so a following poll sees the effect, like the real routes do.
 */
export async function mockDashboard(page: Page, events: Event[]) {
  await page.route('**/api/events', async (route) => {
    const pin = await route.request().headerValue('x-dashboard-pin');
    if (!pin) return route.fulfill({ status: 401, json: { error: 'Unauthorized' } });
    return route.fulfill({ json: { events, truncated: false } });
  });

  await page.route('**/api/sos', async (route) => {
    const body = route.request().postDataJSON() as { sosId: string; lat: number; lon: number };
    events.push({
      id: `dash-sosack-e2e-${events.length}`, type: 'sos_state', lat: body.lat, lon: body.lon, featureRef: null,
      severity: null, waterLevel: null, authorId: 'dashboard', authorName: 'Dashboard', authorRole: 'official',
      timestampMs: Date.now(), expiresAt: Date.now() + 12 * 3_600_000, origin: 'server', hopCount: 0,
      note: null, disputeReason: null, payload: JSON.stringify({ sosId: body.sosId, state: 'ACKNOWLEDGED' }),
    });
    await route.fulfill({ json: { ok: true } });
  });
}
