import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const location = useLocation();
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}

/** Admin-only screens. The database enforces the same rule; this just hides the UI. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  if (session.role !== 'admin') {
    return (
      <div className="page">
        <h1>Admins only</h1>
        <p className="muted">This area is limited to portal administrators.</p>
      </div>
    );
  }
  return <>{children}</>;
}
