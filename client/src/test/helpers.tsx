import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthContext } from '../auth/AuthContext';
import type { AuthValue } from '../auth/AuthContext';
import type { Session } from '../auth/session';

export function makeSession(overrides: Partial<Session> = {}): Session {
  return {
    jwt: 'test.jwt.token',
    analystId: '11111111-1111-1111-1111-111111111111',
    name: 'Dana Analyst',
    role: 'user',
    exp: Math.floor(Date.now() / 1000) + 3600,
    ...overrides,
  };
}

export function makeAuth(overrides: Partial<AuthValue> = {}): AuthValue {
  return {
    session: makeSession(),
    kdToken: 'kd-token',
    kdTokenLoading: false,
    refreshKdToken: vi.fn(async () => {}),
    login: vi.fn(async () => {}),
    logout: vi.fn(),
    ...overrides,
  };
}

export function renderWithAuth(ui: ReactNode, auth: AuthValue = makeAuth(), route = '/') {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </AuthContext.Provider>,
  );
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
