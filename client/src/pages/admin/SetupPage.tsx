import { useCallback, useEffect, useState } from 'react';
import * as kd from '../../api/knowledgeDrop';
import * as portalDb from '../../api/portalDb';
import { SETTING_KEYS } from '../../api/types';
import type { HealthResponse } from '../../api/types';
import { useAuth, useSession } from '../../auth/AuthContext';
import { ErrorBanner, InfoBanner } from '../../components/common';
import { describeError } from '../../lib/errors';
import { formatDateTime, maskToken } from '../../lib/format';

interface CurrentClient {
  clientId?: string;
  clientName?: string;
  token?: string;
  updatedAt?: string;
}

export function SetupPage() {
  const session = useSession();
  const { refreshKdToken } = useAuth();
  const [current, setCurrent] = useState<CurrentClient>({});
  const [reveal, setReveal] = useState(false);
  const [registerName, setRegisterName] = useState('Harborline Health');
  const [pasteId, setPasteId] = useState('');
  const [pasteToken, setPasteToken] = useState('');
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const rows = await portalDb.listSettings(session.jwt);
      const get = (key: string) => rows.find((r) => r.key === key);
      setCurrent({
        clientId: get(SETTING_KEYS.clientId)?.value,
        clientName: get(SETTING_KEYS.clientName)?.value,
        token: get(SETTING_KEYS.clientToken)?.value,
        updatedAt: get(SETTING_KEYS.clientToken)?.updated_at,
      });
    } catch (err) {
      setError(describeError(err));
    }
  }, [session.jwt]);

  useEffect(() => {
    void load();
  }, [load]);

  async function save(values: Record<string, string>, message: string) {
    await portalDb.saveSettings(session.jwt, values);
    await Promise.all([load(), refreshKdToken()]);
    setNotice(message);
  }

  const confirmReplace = () =>
    !current.token ||
    window.confirm(
      'Replace the current knowledgeDrop client? Documents uploaded under the old client will no longer be visible in the portal.',
    );

  return (
    <div className="page narrow">
      <h1>knowledgeDrop setup</h1>
      <p className="muted">
        The portal uses one knowledgeDrop client for the whole company. Every analyst&apos;s questions and
        uploads go through it. Only administrators can see this page.
      </p>
      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      {notice && <InfoBanner>{notice}</InfoBanner>}

      <section className="card form-card">
        <h2>Current client</h2>
        {current.token ? (
          <dl className="kv">
            <dt>Name</dt>
            <dd>{current.clientName ?? '—'}</dd>
            <dt>Client ID</dt>
            <dd className="mono">{current.clientId ?? '—'}</dd>
            <dt>Client token</dt>
            <dd className="mono token-row">
              {reveal ? current.token : maskToken(current.token)}
              <button type="button" className="link-button" onClick={() => setReveal(!reveal)}>
                {reveal ? 'Hide' : 'Show'}
              </button>
              <button
                type="button"
                className="link-button"
                onClick={() => void navigator.clipboard?.writeText(current.token ?? '')}
              >
                Copy
              </button>
            </dd>
            {current.updatedAt && (
              <>
                <dt>Last changed</dt>
                <dd>{formatDateTime(current.updatedAt)}</dd>
              </>
            )}
          </dl>
        ) : (
          <p className="muted">No client configured yet. Register one below.</p>
        )}
        <div className="row">
          <button
            type="button"
            className="secondary"
            onClick={async () => {
              setHealth(null);
              try {
                setHealth(await kd.health());
              } catch (err) {
                setError(`Health check failed: ${describeError(err)}`);
              }
            }}
          >
            Check service health
          </button>
          {health && (
            <span className={health.status === 'up' ? 'ok' : 'warn'}>
              Service {health.status}, Elasticsearch {health.elasticsearch}
            </span>
          )}
        </div>
      </section>

      <form
        className="card form-card"
        onSubmit={async (e) => {
          e.preventDefault();
          setError(null);
          if (!confirmReplace()) return;
          try {
            const res = await kd.registerClient(registerName.trim());
            await save(
              {
                [SETTING_KEYS.clientId]: res.clientId,
                [SETTING_KEYS.clientName]: registerName.trim(),
                [SETTING_KEYS.clientToken]: res.clientToken,
              },
              'Registered a new knowledgeDrop client and saved its token.',
            );
            setReveal(true);
          } catch (err) {
            setError(`Registration failed: ${describeError(err)}`);
          }
        }}
      >
        <h2>Register a new client</h2>
        <p className="muted small">
          Calls <code>POST /knowledgeDrop/client/register</code>. The service shows the token only once; the
          portal stores it for you.
        </p>
        <label className="label" htmlFor="regName">
          Client name
        </label>
        <input
          id="regName"
          value={registerName}
          maxLength={255}
          onChange={(e) => setRegisterName(e.target.value)}
          required
        />
        <button type="submit">Register client</button>
      </form>

      <form
        className="card form-card"
        onSubmit={async (e) => {
          e.preventDefault();
          setError(null);
          if (!confirmReplace()) return;
          try {
            await save(
              {
                [SETTING_KEYS.clientId]: pasteId.trim(),
                [SETTING_KEYS.clientName]: current.clientName ?? 'Harborline Health',
                [SETTING_KEYS.clientToken]: pasteToken.trim(),
              },
              'Saved the existing client token.',
            );
            setPasteId('');
            setPasteToken('');
          } catch (err) {
            setError(describeError(err));
          }
        }}
      >
        <h2>Use an existing client</h2>
        <label className="label" htmlFor="pasteId">
          Client ID
        </label>
        <input id="pasteId" value={pasteId} onChange={(e) => setPasteId(e.target.value)} required />
        <label className="label" htmlFor="pasteToken">
          Client token
        </label>
        <input
          id="pasteToken"
          type="password"
          value={pasteToken}
          onChange={(e) => setPasteToken(e.target.value)}
          required
        />
        <button type="submit" className="secondary">
          Save token
        </button>
      </form>
    </div>
  );
}
