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
  return i.event.type === 'flood_report' && i.event.severity === 'S3' && !i.stale ? 's3' : null;
}

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
      seen.current = new Set(items.map((i) => i.id));
      return;
    }
    const fresh = items.filter((i) => !seen.current!.has(i.id));
    fresh.forEach((i) => seen.current!.add(i.id));

    const sos = fresh.filter((i) => alarmKind(i) === 'sos');
    const s3 = fresh.filter((i) => alarmKind(i) === 's3');
    if (sos.length === 0 && s3.length === 0) return;

    const kind = sos.length > 0 ? 'sos' : 's3';
    const text =
      sos.length > 0
        ? `New SOS request${sos.length > 1 ? `s (${sos.length})` : ''}`
        : s3[0].kind === 'report'
          ? `New ${s3[0].event.severity} report: ${SEVERITY_LABEL[s3[0].event.severity ?? ''] ?? ''}${s3.length > 1 ? ` (+${s3.length - 1} more)` : ''}`
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
