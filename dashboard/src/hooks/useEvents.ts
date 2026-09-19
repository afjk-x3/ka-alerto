'use client';

import { useState, useCallback } from 'react';
import { Event, AuthError } from '@/lib/types';
import { fetchEvents } from '@/lib/api';

/** Refetches everything on each refresh (Supabase has no cursor; a barangay's volume is tiny). No auto-polling. */
export function useEvents(onAuthFail: () => void) {
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<number | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setEvents(await fetchEvents());
      setUpdatedAt(Date.now());
    } catch (e) {
      if (e instanceof AuthError) onAuthFail();
      else setError(e instanceof Error ? e.message : 'Something went wrong');
    } finally {
      setLoading(false);
    }
  }, [onAuthFail]);

  return { events, loading, error, updatedAt, refresh };
}
