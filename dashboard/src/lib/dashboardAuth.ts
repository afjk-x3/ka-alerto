// The one PIN check shared by every API route (events, evac, photo). Two things a plain
// `header !== pin` comparison was missing (2021-09-22 review, D18): a wrong guess took no
// longer to reject than a right one (a timing side-channel, `timingSafeEqual` below), and
// nothing stopped a script from trying PINs as fast as it could (the per-IP lockout below).
// Demo-scope note stands regardless: the anon key ships in the APK, so this guards the
// dashboard's door, not the data behind it (see CLAUDE.md).
import { timingSafeEqual } from 'node:crypto';

const WINDOW_MS = 60_000;
const MAX_ATTEMPTS = 5;
const LOCKOUT_MS = 30_000;

interface Attempts {
  count: number;
  windowStart: number;
  lockedUntil: number;
}

// Per server process, not per deployment — a restart clears it, which is fine at this scope.
// ponytail: an in-memory Map is wrong for a multi-instance deployment (each instance has its
// own count); a single dashboard server is the assumption everywhere else in this codebase too.
const attempts = new Map<string, Attempts>();

function clientIp(request: Request): string {
  return request.headers.get('x-forwarded-for')?.split(',')[0]?.trim() || 'unknown';
}

function constantTimeEqual(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  // A length check leaks the given string's length, not the PIN's — timingSafeEqual requires
  // equal-length buffers anyway, so there is no safe way around checking this first.
  if (bufA.length !== bufB.length) return false;
  return timingSafeEqual(bufA, bufB);
}

/**
 * Null means "let the request through". Otherwise, the `Response` to send back as-is:
 * 403 when a write route requires `DASHBOARD_PIN` and none is set, 429 while locked out,
 * 401 for a wrong or missing PIN. A correct PIN resets that IP's count.
 */
export function checkDashboardPin(request: Request, opts: { requirePin?: boolean } = {}): Response | null {
  const pin = process.env.DASHBOARD_PIN;
  if (!pin) {
    return opts.requirePin
      ? Response.json({ error: 'writes need DASHBOARD_PIN to be set on the server' }, { status: 403 })
      : null;
  }

  const ip = clientIp(request);
  const now = Date.now();
  const entry = attempts.get(ip);
  if (entry && entry.lockedUntil > now) {
    const waitSec = Math.ceil((entry.lockedUntil - now) / 1000);
    return Response.json({ error: `Too many attempts. Try again in ${waitSec}s.` }, { status: 429 });
  }

  if (constantTimeEqual(request.headers.get('x-dashboard-pin') ?? '', pin)) {
    attempts.delete(ip);
    return null;
  }

  const fresh = entry && entry.windowStart > now - WINDOW_MS ? entry : { count: 0, windowStart: now, lockedUntil: 0 };
  fresh.count += 1;
  if (fresh.count >= MAX_ATTEMPTS) {
    attempts.set(ip, { count: 0, windowStart: now, lockedUntil: now + LOCKOUT_MS });
  } else {
    attempts.set(ip, fresh);
  }
  return Response.json({ error: 'unauthorized' }, { status: 401 });
}
