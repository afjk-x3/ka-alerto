import { Event, AuthError } from './types';

const PIN_KEY = 'kaalerto_dashboard_pin';

export const getPin = (): string | null =>
  typeof window === 'undefined' ? null : sessionStorage.getItem(PIN_KEY);
export const setPin = (pin: string) => sessionStorage.setItem(PIN_KEY, pin);
export const clearPin = () => sessionStorage.removeItem(PIN_KEY);

/** Every event, via the dashboard's own /api/events route (which holds the PIN check and the Supabase key). */
export async function fetchEvents(): Promise<Event[]> {
  const pin = getPin();
  let res: Response;
  try {
    res = await fetch('/api/events', { headers: pin ? { 'X-Dashboard-Pin': pin } : {}, cache: 'no-store' });
  } catch {
    throw new Error("Can't reach the dashboard server.");
  }
  if (res.status === 401) throw new AuthError();
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error ?? `Server error (${res.status})`);
  return body.events;
}

/** A report's photo as an object URL, or null if it hasn't been uploaded (only the phone that took it has it until then). */
export async function fetchPhoto(hash: string): Promise<string | null> {
  const pin = getPin();
  const res = await fetch(`/api/photo/${hash}`, { headers: pin ? { 'X-Dashboard-Pin': pin } : {} });
  if (res.status === 401) throw new AuthError();
  if (!res.ok) return null;
  return URL.createObjectURL(await res.blob());
}

/** A shelter write (add, open, close, remove) via the dashboard's own PIN-checked /api/evac route. */
export async function postEvac(body: Record<string, unknown>): Promise<void> {
  const pin = getPin();
  let res: Response;
  try {
    res = await fetch('/api/evac', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(pin ? { 'X-Dashboard-Pin': pin } : {}) },
      body: JSON.stringify(body),
    });
  } catch {
    throw new Error("Can't reach the dashboard server.");
  }
  if (res.status === 401) throw new AuthError();
  if (!res.ok) throw new Error(((await res.json().catch(() => ({}))) as { error?: string }).error ?? `Server error (${res.status})`);
}
