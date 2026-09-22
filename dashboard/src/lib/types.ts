export interface Event {
  id: string;
  type: string;
  lat: number;
  lon: number;
  featureRef: string | null;
  severity: string | null;
  waterLevel: string | null;
  authorId: string;
  authorName: string;
  authorRole: string;
  timestampMs: number;
  expiresAt: number;
  origin: string;
  hopCount: number;
  note: string | null;
  disputeReason: string | null;
  payload: string | null;
}

export class AuthError extends Error {
  /** 401 (wrong or missing PIN) or 429 (too many wrong attempts — see lib/dashboardAuth.ts). */
  status: number;
  constructor(message = 'Unauthorized', status = 401) {
    super(message);
    this.name = 'AuthError';
    this.status = status;
  }
}
