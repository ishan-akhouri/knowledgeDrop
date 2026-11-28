import { useCallback, useEffect, useState } from 'react';
import * as portalDb from '../api/portalDb';
import type { BookmarkRecord } from '../api/types';
import { useSession } from '../auth/AuthContext';
import { AnswerCard } from '../components/AnswerCard';
import { ErrorBanner } from '../components/common';
import { describeError } from '../lib/errors';
import { useDownload } from '../lib/useDownload';
import { useOwnerNames } from '../lib/useOwnerNames';

export function SavedPage() {
  const session = useSession();
  const isAdmin = session.role === 'admin';
  const { ownerOf, nameOf } = useOwnerNames();
  const { download, error: downloadError, clearError } = useDownload();
  const [rows, setRows] = useState<BookmarkRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await portalDb.listBookmarks(session.jwt));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }, [session.jwt]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(id: string) {
    try {
      await portalDb.removeBookmark(session.jwt, id);
      setRows((prev) => prev.filter((b) => b.id !== id));
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <div className="page">
      <h1>{isAdmin ? 'Saved answers (all analysts)' : 'My saved answers'}</h1>
      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      <ErrorBanner message={downloadError} onDismiss={clearError} />

      {loading ? (
        <p className="muted">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="muted">Nothing saved yet. Use “Save answer” on any answer you want to keep.</p>
      ) : (
        rows.map((b) =>
          b.question ? (
            <div key={b.id} className="saved-item">
              {(b.note || isAdmin) && (
                <p className="saved-note">
                  {isAdmin && <strong>{nameOf(b.analyst_id)}: </strong>}
                  {b.note || <span className="muted">no note</span>}
                </p>
              )}
              <AnswerCard
                record={b.question}
                ownerOf={ownerOf}
                onDownload={download}
                askedBy={isAdmin ? nameOf(b.question.analyst_id) : undefined}
                actions={
                  (isAdmin || b.analyst_id === session.analystId) && (
                    <button type="button" className="link-button danger" onClick={() => remove(b.id)}>
                      Remove from saved
                    </button>
                  )
                }
              />
            </div>
          ) : null,
        )
      )}
    </div>
  );
}
