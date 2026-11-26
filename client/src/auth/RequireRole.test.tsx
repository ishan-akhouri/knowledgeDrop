import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import * as portalDb from '../api/portalDb';
import { Layout } from '../components/Layout';
import { makeAuth, makeSession } from '../test/helpers';
import { AuthContext } from './AuthContext';
import type { AuthValue } from './AuthContext';
import { RequireAdmin, RequireAuth } from './RequireRole';

function renderAt(path: string, auth: AuthValue) {
  vi.spyOn(portalDb, 'listSettings').mockResolvedValue([]);
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<p>login screen</p>} />
          <Route
            element={
              <RequireAuth>
                <Layout />
              </RequireAuth>
            }
          >
            <Route path="/ask" element={<p>ask screen</p>} />
            <Route
              path="/admin/setup"
              element={
                <RequireAdmin>
                  <p>secret setup</p>
                </RequireAdmin>
              }
            />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe('route guards', () => {
  it('sends signed-out visitors to the login screen', () => {
    renderAt('/ask', makeAuth({ session: null }));
    expect(screen.getByText('login screen')).toBeInTheDocument();
  });

  it('blocks analysts from admin screens and hides the admin nav', () => {
    renderAt('/admin/setup', makeAuth());
    expect(screen.queryByText('secret setup')).not.toBeInTheDocument();
    expect(screen.getByText('Admins only')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'knowledgeDrop setup' })).not.toBeInTheDocument();
  });

  it('lets admins in and shows the admin nav', () => {
    renderAt('/admin/setup', makeAuth({ session: makeSession({ role: 'admin' }) }));
    expect(screen.getByText('secret setup')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'knowledgeDrop setup' })).toBeInTheDocument();
  });
});
