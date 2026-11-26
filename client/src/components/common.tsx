import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';

export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status}`}>{status}</span>;
}

export function ErrorBanner({ message, onDismiss }: { message: string | null; onDismiss?: () => void }) {
  if (!message) return null;
  return (
    <div className="banner banner-error" role="alert">
      <span>{message}</span>
      {onDismiss && (
        <button type="button" className="link-button" onClick={onDismiss} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  );
}

export function InfoBanner({ children }: { children: ReactNode }) {
  return <div className="banner banner-info">{children}</div>;
}

/** Shown on screens that need the knowledgeDrop token when none is configured. */
export function KdTokenNotice() {
  const { session, kdToken, kdTokenLoading } = useAuth();
  if (kdToken || kdTokenLoading) return null;
  return (
    <InfoBanner>
      The portal is not connected to knowledgeDrop yet.{' '}
      {session?.role === 'admin' ? (
        <Link to="/admin/setup">Finish Setup</Link>
      ) : (
        'Ask a portal administrator to finish Setup.'
      )}
    </InfoBanner>
  );
}
