import { useCallback, useEffect, useState } from 'react';
import * as portalDb from '../../api/portalDb';
import type { Analyst, AppRole } from '../../api/types';
import { useSession } from '../../auth/AuthContext';
import { ErrorBanner, InfoBanner } from '../../components/common';
import { describeError } from '../../lib/errors';
import { formatShort } from '../../lib/format';

const EMPTY = { username: '', display_name: '', department: '', role: 'user' as AppRole, password: '' };

export function AnalystsPage() {
  const session = useSession();
  const [rows, setRows] = useState<Analyst[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setRows(await portalDb.listAnalysts(session.jwt));
    } catch (err) {
      setError(describeError(err));
    }
  }, [session.jwt]);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(action: () => Promise<void>, message: string) {
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(message);
      await load();
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <div className="page">
      <h1>Analyst accounts</h1>
      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      {notice && <InfoBanner>{notice}</InfoBanner>}

      <form
        className="card form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          void run(async () => {
            await portalDb.createAnalyst(session.jwt, { ...form, username: form.username.trim() });
            setForm(EMPTY);
          }, `Created ${form.display_name}.`);
        }}
      >
        <h2 className="span-all">Add an analyst</h2>
        <label>
          <span className="label">Username</span>
          <input
            value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
            required
          />
        </label>
        <label>
          <span className="label">Display name</span>
          <input
            value={form.display_name}
            onChange={(e) => setForm({ ...form, display_name: e.target.value })}
            required
          />
        </label>
        <label>
          <span className="label">Department</span>
          <input
            value={form.department}
            placeholder="e.g. Pharmacy / 340B"
            onChange={(e) => setForm({ ...form, department: e.target.value })}
          />
        </label>
        <label>
          <span className="label">Role</span>
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as AppRole })}>
            <option value="user">Analyst</option>
            <option value="admin">Administrator</option>
          </select>
        </label>
        <label>
          <span className="label">Temporary password (8+)</span>
          <input
            type="password"
            minLength={8}
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required
          />
        </label>
        <div className="form-grid-submit">
          <button type="submit">Create account</button>
        </div>
      </form>

      <div className="card table-card">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Username</th>
              <th>Department</th>
              <th>Role</th>
              <th>Status</th>
              <th>Created</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {rows.map((a) => {
              const isMe = a.id === session.analystId;
              return (
                <tr key={a.id} className={a.active ? '' : 'inactive'}>
                  <td className="strong">
                    {a.display_name}
                    {isMe && <span className="muted"> (you)</span>}
                  </td>
                  <td className="mono small">{a.username}</td>
                  <td className="small">{a.department ?? '—'}</td>
                  <td>
                    <select
                      aria-label={`Role for ${a.display_name}`}
                      value={a.role}
                      disabled={isMe}
                      onChange={(e) =>
                        void run(
                          () => portalDb.updateAnalystRole(session.jwt, a.id, e.target.value as AppRole),
                          `Updated ${a.display_name}'s role. It applies at their next sign-in.`,
                        )
                      }
                    >
                      <option value="user">Analyst</option>
                      <option value="admin">Administrator</option>
                    </select>
                  </td>
                  <td>{a.active ? 'Active' : 'Disabled'}</td>
                  <td className="small nowrap">{formatShort(a.created_at)}</td>
                  <td className="row-actions">
                    <button
                      type="button"
                      className="link-button"
                      onClick={() => {
                        const pw = window.prompt(`New password for ${a.display_name} (8+ characters):`);
                        if (pw) {
                          void run(() => portalDb.resetPassword(session.jwt, a.id, pw), 'Password reset.');
                        }
                      }}
                    >
                      Reset password
                    </button>
                    {!isMe && (
                      <button
                        type="button"
                        className="link-button danger"
                        onClick={() =>
                          void run(
                            () => portalDb.setActive(session.jwt, a.id, !a.active),
                            a.active ? `Disabled ${a.display_name}.` : `Re-enabled ${a.display_name}.`,
                          )
                        }
                      >
                        {a.active ? 'Disable' : 'Enable'}
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
