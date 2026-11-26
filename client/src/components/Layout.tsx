import { NavLink, Outlet } from 'react-router-dom';
import { useAuth, useSession } from '../auth/AuthContext';

export function Layout() {
  const session = useSession();
  const { logout } = useAuth();
  const isAdmin = session.role === 'admin';

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">H</span>
          <div>
            <div className="brand-name">Harborline Health</div>
            <div className="muted small">Analyst Knowledge Portal</div>
          </div>
        </div>
        <nav>
          <NavLink to="/ask">Ask</NavLink>
          <NavLink to="/history">History</NavLink>
          <NavLink to="/saved">Saved answers</NavLink>
          <NavLink to="/library">Document library</NavLink>
          <NavLink to="/upload">Upload</NavLink>
          {isAdmin && (
            <>
              <div className="nav-section">Admin</div>
              <NavLink to="/admin/setup">knowledgeDrop setup</NavLink>
              <NavLink to="/admin/analysts">Analyst accounts</NavLink>
            </>
          )}
        </nav>
        <div className="sidebar-foot">
          <NavLink to="/account" className="me">
            <span className="avatar">{session.name.slice(0, 1).toUpperCase()}</span>
            <span>
              <span className="me-name">{session.name}</span>
              <span className="muted small">{isAdmin ? 'Administrator' : 'Analyst'}</span>
            </span>
          </NavLink>
          <button type="button" className="link-button" onClick={logout}>
            Sign out
          </button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
