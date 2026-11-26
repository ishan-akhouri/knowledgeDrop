import { useState } from 'react';
import * as portalDb from '../api/portalDb';
import { useSession } from '../auth/AuthContext';
import { ErrorBanner, InfoBanner } from '../components/common';
import { describeError } from '../lib/errors';

export function AccountPage() {
  const session = useSession();
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const roleLabel = session.role === 'admin' ? 'administrator' : 'analyst';

  return (
    <div className="page narrow">
      <h1>My account</h1>
      <p>
        Signed in as <strong>{session.name}</strong>, {roleLabel}.
      </p>
      <form
        className="card form-card"
        onSubmit={async (e) => {
          e.preventDefault();
          setError(null);
          setDone(false);
          if (newPassword !== confirm) {
            setError('The new passwords do not match.');
            return;
          }
          try {
            await portalDb.changeMyPassword(session.jwt, oldPassword, newPassword);
            setDone(true);
            setOldPassword('');
            setNewPassword('');
            setConfirm('');
          } catch (err) {
            setError(describeError(err));
          }
        }}
      >
        <h2>Change password</h2>
        <ErrorBanner message={error} />
        {done && <InfoBanner>Password changed.</InfoBanner>}
        <label className="label" htmlFor="old">
          Current password
        </label>
        <input
          id="old"
          type="password"
          value={oldPassword}
          onChange={(e) => setOldPassword(e.target.value)}
          required
        />
        <label className="label" htmlFor="new">
          New password (8+ characters)
        </label>
        <input
          id="new"
          type="password"
          minLength={8}
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
        />
        <label className="label" htmlFor="confirm">
          Confirm new password
        </label>
        <input
          id="confirm"
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          required
        />
        <button type="submit">Change password</button>
      </form>
    </div>
  );
}
