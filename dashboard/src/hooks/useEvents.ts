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
  const [truncated, setTruncated] = useState(false);
  // True until the very first refresh settles, success or not — the page renders nothing but
  // the logo while this is true, so a locked dashboard never flashes its own (empty) shell
  // before the PIN gate appears (found 21 Sep). Every later refresh leaves it alone.
  const [checking, setChecking] = useState(true);
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
        const result = await fetchEvents();
        setEvents(result.events);
        setTruncated(result.truncated);
        setUpdatedAt(Date.now());
        setError(null);
      } catch (e) {
        if (e instanceof AuthError) onAuthFail();
        else setError(e instanceof Error ? e.message : 'Something went wrong');
      } finally {
        inFlight.current = false;
        if (!opts?.silent) setLoading(false);
        setChecking(false);
      }
    },
    [onAuthFail],
  );

  /** Drops everything held in memory (used on log out). */
  const reset = useCallback(() => {
    setEvents([]);
    setError(null);
    setUpdatedAt(null);
    setTruncated(false);
  }, []);

  return { events, loading, error, updatedAt, truncated, checking, refresh, reset };
}
