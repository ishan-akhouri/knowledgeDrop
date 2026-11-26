import type { AppRole } from '../api/types';

export interface Session {
  jwt: string;
  analystId: string;
  name: string;
  role: AppRole;
  /** Expiry, seconds since epoch. */
  exp: number;
}

const STORAGE_KEY = 'harborline.session';

function base64UrlDecode(input: string): string {
  const b64 = input.replace(/-/g, '+').replace(/_/g, '/');
  const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/** Reads the claims of a JWT issued by insurance_portal.login. Does not verify it. */
export function sessionFromJwt(jwt: string): Session {
  const parts = jwt.split('.');
  if (parts.length !== 3) throw new Error('Malformed token');
  const claims = JSON.parse(base64UrlDecode(parts[1])) as {
    role?: string;
    sub?: string;
    name?: string;
    exp?: number;
  };
  if (!claims.sub || !claims.role || !claims.exp) throw new Error('Token is missing claims');
  return {
    jwt,
    analystId: claims.sub,
    name: claims.name ?? 'Analyst',
    role: claims.role === 'portal_admin' ? 'admin' : 'user',
    exp: claims.exp,
  };
}

export function isExpired(session: Session, nowMs: number = Date.now()): boolean {
  return session.exp * 1000 <= nowMs;
}

export function loadSession(): Session | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const session = sessionFromJwt(raw);
    return isExpired(session) ? null : session;
  } catch {
    return null;
  }
}

export function storeSession(session: Session | null): void {
  if (session) sessionStorage.setItem(STORAGE_KEY, session.jwt);
  else sessionStorage.removeItem(STORAGE_KEY);
}
