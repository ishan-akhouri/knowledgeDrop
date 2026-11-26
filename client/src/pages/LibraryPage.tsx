import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as kd from '../api/knowledgeDrop';
import * as portalDb from '../api/portalDb';
import type { DocumentStatus, DocumentSummary } from '../api/types';
import { useAuth, useSession } from '../auth/AuthContext';
import { ErrorBanner, InfoBanner, KdTokenNotice, StatusBadge } from '../components/common';
import { describeError } from '../lib/errors';
import { formatShort } from '../lib/format';
import { useDownload } from '../lib/useDownload';
import { useOwnerNames } from '../lib/useOwnerNames';

const PAGE_SIZE = 25;

export function LibraryPage() {
  const session = useSession();
  const { kdToken } = useAuth();
  const { ownerOf, reload: reloadOwners } = useOwnerNames();
  const { download, error: downloadError, clearError } = useDownload();
  const [status, setStatus] = useState<DocumentStatus | ''>('');
  const [offset, setOffset] = useState(0);
  const [docs, setDocs] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!kdToken) return;
    setLoading(true);
    setError(null);
    try {
      setDocs(await kd.listDocuments(kdToken, { status: status || undefined, limit: PAGE_SIZE, offset }));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }, [kdToken, status, offset]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(doc: DocumentSummary) {
    if (!kdToken) return;
    if (!window.confirm(`Delete “${doc.filename}” from the knowledge base for everyone?`)) return;
    setNotice(null);
    try {
      const { pendingCleanup } = await kd.deleteDocument(kdToken, doc.filename);
      await portalDb.markOwnerDeleted(session.jwt, doc.filename, session.analystId);
      setNotice(
        pendingCleanup
          ? `Deleted “${doc.filename}”. The server reported that search-index cleanup is still pending.`
          : `Deleted “${doc.filename}”.`,
      );
      await Promise.all([load(), reloadOwners()]);
    } catch (err) {
      setError(`Could not delete ${doc.filename}: ${describeError(err)}`);
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>Document library</h1>
        <div className="page-head-actions">
          <label className="inline-label">
            Status
            <select
              aria-label="Status filter"
              value={status}
              onChange={(e) => {
                setOffset(0);
                setStatus(e.target.value as DocumentStatus | '');
              }}
            >
              <option value="">All</option>
              <option value="ready">Ready</option>
              <option value="processing">Processing</option>
              <option value="failed">Failed</option>
            </select>
          </label>
          <button type="button" className="secondary" onClick={() => void load()} disabled={!kdToken}>
            Refresh
          </button>
          <Link to="/upload" className="button">
            Upload
          </Link>
        </div>
      </div>

      <KdTokenNotice />
      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      <ErrorBanner message={downloadError} onDismiss={clearError} />
      {notice && <InfoBanner>{notice}</InfoBanner>}

      <div className="card table-card">
        <table>
          <thead>
            <tr>
              <th>Document</th>
              <th className="hide-narrow">Type</th>
              <th>Uploaded</th>
              <th>Owner</th>
              <th>Status</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {docs.map((d) => (
              <tr key={d.documentId}>
                <td className="strong filename">{d.filename}</td>
                <td className="muted small hide-narrow">{d.type}</td>
                <td className="small nowrap">{formatShort(d.uploadTime)}</td>
                <td className="small nowrap">{ownerOf(d.documentId)}</td>
                <td>
                  <StatusBadge status={d.status} />
                </td>
                <td className="row-actions">
                  <button type="button" className="link-button" onClick={() => void download(d.filename)}>
                    Download
                  </button>
                  <button type="button" className="link-button danger" onClick={() => void remove(d)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
            {!loading && docs.length === 0 && (
              <tr>
                <td colSpan={6} className="muted empty">
                  {kdToken ? 'No documents match.' : 'Not connected.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="pager">
        <button
          type="button"
          className="secondary"
          disabled={offset === 0 || loading}
          onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}
        >
          ← Previous
        </button>
        <span className="muted small">{docs.length > 0 ? `${offset + 1}–${offset + docs.length}` : '0'}</span>
        <button
          type="button"
          className="secondary"
          disabled={docs.length < PAGE_SIZE || loading}
          onClick={() => setOffset(offset + PAGE_SIZE)}
        >
          Next →
        </button>
      </div>
    </div>
  );
}
