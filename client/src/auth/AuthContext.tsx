import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import * as portalDb from '../api/portalDb';
import { isExpired, loadSession, sessionFromJwt, storeSession } from './session';
import type { Session } from './session';

export interface AuthValue {
  session: Session | null;
  /** knowledgeDrop client token; null until loaded or when no client is configured. */
  kdToken: string | null;
  kdTokenLoading: boolean;
  refreshKdToken: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthValue | null>(null);

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}

/** Like useAuth, but for screens behind <RequireAuth>, where a session always exists. */
export function useSession(): Session {
  const { session } = useAuth();
  if (!session) throw new Error('No session');
  return session;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [kdToken, setKdToken] = useState<string | null>(null);
  const [kdTokenLoading, setKdTokenLoading] = useState(false);

  const logout = useCallback(() => {
    storeSession(null);
    setSession(null);
    setKdToken(null);
  }, []);

  const refreshKdToken = useCallback(async () => {
    if (!session) return;
    setKdTokenLoading(true);
    try {
      setKdToken(await portalDb.fetchKdToken(session.jwt));
    } catch {
      setKdToken(null);
    } finally {
      setKdTokenLoading(false);
    }
  }, [session]);

  const login = useCallback(async (username: string, password: string) => {
    const jwt = await portalDb.login(username, password);
    const next = sessionFromJwt(jwt);
    storeSession(next);
    setSession(next);
  }, []);

  // A rejected JWT anywhere in the app signs the analyst out.
  useEffect(() => {
    portalDb.setUnauthorizedHandler(logout);
    return () => portalDb.setUnauthorizedHandler(null);
  }, [logout]);

  // Sign out when the 8-hour token expires.
  useEffect(() => {
    if (!session) return;
    if (isExpired(session)) {
      logout();
      return;
    }
    const ms = Math.min(session.exp * 1000 - Date.now(), 2 ** 31 - 1);
    const timer = setTimeout(logout, ms);
    return () => clearTimeout(timer);
  }, [session, logout]);

  useEffect(() => {
    void refreshKdToken();
  }, [refreshKdToken]);

  const value = useMemo<AuthValue>(
    () => ({ session, kdToken, kdTokenLoading, refreshKdToken, login, logout }),
    [session, kdToken, kdTokenLoading, refreshKdToken, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
