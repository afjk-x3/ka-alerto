'use client';

import { useState, useCallback, useRef } from 'react';
import { Event, AuthError } from '@/lib/types';
import { fetchEvents } from '@/lib/api';

/**
 * Refetches everything each time (Supabase has no cursor; a barangay's volume is tiny —
 * ponytail: switch to `timestampMs=gt.` or Supabase Realtime if this ever gets heavy).
 * A `silent` refresh is the background poll: it never toggles `loading`, so nothing flashes.
 */
export function useEvents(onAuthFail: () => void) {
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<number | null>(null);
  const inFlight = useRef(false);

  const refresh = useCallback(
    async (opts?: { silent?: boolean }) => {
      if (inFlight.current) return;
      inFlight.current = true;
      if (!opts?.silent) {
        setLoading(true);
        setError(null);
      }
      try {
        setEvents(await fetchEvents());
        setUpdatedAt(Date.now());
        setError(null);
      } catch (e) {
        if (e instanceof AuthError) onAuthFail();
        else setError(e instanceof Error ? e.message : 'Something went wrong');
      } finally {
        inFlight.current = false;
        if (!opts?.silent) setLoading(false);
      }
    },
    [onAuthFail],
  );

  /** Drops everything held in memory (used on log out). */
  const reset = useCallback(() => {
    setEvents([]);
    setError(null);
    setUpdatedAt(null);
  }, []);

  return { events, loading, error, updatedAt, refresh, reset };
}
