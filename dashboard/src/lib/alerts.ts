// Browser alarm: a Web Audio siren (no sound file needed) plus a system notification.
// Browsers only allow sound and notification permission after a user click, which is why
// the dashboard has an explicit "Turn on alerts" button.

export type AlertKind = 'sos' | 's3';

let ctx: AudioContext | null = null;

function audio(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!ctx) {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    ctx = new Ctor();
  }
  return ctx;
}

/** Call from a click handler (or any pointer event) so later alarms are allowed to play. */
export function unlockAudio(): void {
  void audio()?.resume();
}

function tone(c: AudioContext, freq: number, start: number, dur: number) {
  const osc = c.createOscillator();
  const gain = c.createGain();
  osc.type = 'square';
  osc.frequency.value = freq;
  // Short ramps in and out avoid the click a hard start/stop makes.
  gain.gain.setValueAtTime(0.0001, start);
  gain.gain.exponentialRampToValueAtTime(0.25, start + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, start + dur);
  osc.connect(gain).connect(c.destination);
  osc.start(start);
  osc.stop(start + dur + 0.02);
}

export function playAlarm(kind: AlertKind): void {
  const c = audio();
  if (!c) return;
  void c.resume().then(() => {
    if (c.state !== 'running') return; // still blocked: the banner and tab title carry the alert
    const t = c.currentTime + 0.05;
    if (kind === 'sos') {
      for (let i = 0; i < 8; i++) tone(c, i % 2 ? 660 : 990, t + i * 0.28, 0.26); // siren
    } else {
      tone(c, 740, t, 0.3);
      tone(c, 740, t + 0.45, 0.3);
    }
  });
}

export function notificationsSupported(): boolean {
  return typeof window !== 'undefined' && 'Notification' in window;
}

export function notify(kind: AlertKind, title: string, body: string): void {
  if (!notificationsSupported() || Notification.permission !== 'granted') return;
  try {
    const n = new Notification(title, {
      body,
      tag: `kaalerto-${kind}`,
      requireInteraction: kind === 'sos', // stays up until someone looks at it
    });
    n.onclick = () => {
      window.focus();
      n.close();
    };
  } catch {
    // Some browsers only allow notifications from a service worker; sound and banner still fire.
  }
}
