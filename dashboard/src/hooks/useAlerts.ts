'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Item, SEVERITY_LABEL } from '@/lib/items';
import { notify, notificationsSupported, playAlarm, unlockAudio } from '@/lib/alerts';

const KEY = 'kaalerto_alerts';

export interface AlertBanner {
  text: string;
  kind: 'sos' | 's3';
  at: number;
}

/** What deserves an alarm: an SOS still open, or a current flood report at S3. Nothing else. */
function alarmKind(i: Item): 'sos' | 's3' | null {
  if (i.kind === 'sos') return i.closed ? null : 'sos';
  return i.summary.severity === 'S3' && !i.stale ? 's3' : null;
}

/** An alarm is a spot or request entering an alarming state, so one already at S3 that only gains a confirm stays quiet. */
const alarmKey = (i: Item) => `${i.id}:${alarmKind(i) ?? 'none'}`;

/**
 * Alarms on items that are new since the last check. The first load only sets the baseline
 * (a dashboard opened onto old data must not shout), and the baseline resets on log out.
 */
export function useAlerts(items: Item[], loaded: boolean) {
  const [enabled, setEnabled] = useState(false);
  const [banner, setBanner] = useState<AlertBanner | null>(null);
  const [notif, setNotif] = useState<NotificationPermission | 'unsupported'>('unsupported');
  const seen = useRef<Set<string> | null>(null);

  useEffect(() => {
    setEnabled(localStorage.getItem(KEY) === '1');
    if (notificationsSupported()) setNotif(Notification.permission);
    // Any click unlocks sound, so a reload with alerts already on still works after the first click.
    const unlock = () => unlockAudio();
    window.addEventListener('pointerdown', unlock, { once: true });
    return () => window.removeEventListener('pointerdown', unlock);
  }, []);

  useEffect(() => {
    if (!loaded) {
      seen.current = null;
      return;
    }
    if (seen.current === null) {
      seen.current = new Set(items.map(alarmKey));
      return;
    }
    const fresh = items.filter((i) => !seen.current!.has(alarmKey(i)));
    fresh.forEach((i) => seen.current!.add(alarmKey(i)));

    const sos = fresh.filter((i) => alarmKind(i) === 'sos');
    const s3 = fresh.filter((i) => alarmKind(i) === 's3');
    if (sos.length === 0 && s3.length === 0) return;

    const kind = sos.length > 0 ? 'sos' : 's3';
    const text =
      sos.length > 0
        ? `New SOS request${sos.length > 1 ? `s (${sos.length})` : ''}`
        : s3[0].kind === 'report'
          ? `${s3[0].summary.severity} spot: ${SEVERITY_LABEL[s3[0].summary.severity] ?? ''}${s3.length > 1 ? ` (+${s3.length - 1} more)` : ''}`
          : '';
    setBanner({ text, kind, at: Date.now() });
    if (enabled) {
      playAlarm(kind);
      notify(kind, `KaAlerto: ${text}`, 'Open the dashboard to see where.');
    }
  }, [items, loaded, enabled]);

  const turnOn = useCallback(async () => {
    unlockAudio();
    if (notificationsSupported() && Notification.permission === 'default') {
      await Notification.requestPermission();
    }
    if (notificationsSupported()) setNotif(Notification.permission);
    localStorage.setItem(KEY, '1');
    setEnabled(true);
    playAlarm('s3'); // doubles as the confirmation that sound works
  }, []);

  const turnOff = useCallback(() => {
    localStorage.setItem(KEY, '0');
    setEnabled(false);
  }, []);

  return { enabled, notifOk: notif === 'granted', turnOn, turnOff, banner, dismiss: () => setBanner(null) };
}
