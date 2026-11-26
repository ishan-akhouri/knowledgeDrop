import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as portalDb from '../api/portalDb';
import type { QuestionRecord } from '../api/types';
import { useSession } from '../auth/AuthContext';
import { AnswerCard, BookmarkButton } from '../components/AnswerCard';
import { ErrorBanner } from '../components/common';
import { describeError } from '../lib/errors';
import { formatDateTime } from '../lib/format';
import { useDownload } from '../lib/useDownload';
import { useOwnerNames } from '../lib/useOwnerNames';

export function HistoryPage() {
  const session = useSession();
  const isAdmin = session.role === 'admin';
  const navigate = useNavigate();
  const { ownerOf, nameOf, directory } = useOwnerNames();
  const { download, error: downloadError, clearError } = useDownload();
  // Admins can look at one analyst or everyone; users only ever get their own rows (RLS).
  const [analystFilter, setAnalystFilter] = useState<string>(isAdmin ? '' : session.analystId);
  const [rows, setRows] = useState<QuestionRecord[]>([]);
  const [saved, setSaved] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [questions, bookmarked] = await Promise.all([
        portalDb.listQuestions(session.jwt, { analystId: analystFilter || undefined }),
        portalDb.myBookmarkedQuestionIds(session.jwt, session.analystId),
      ]);
      setRows(questions);
      setSaved(bookmarked);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }, [session.jwt, session.analystId, analystFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  async function bookmark(questionId: string, note: string) {
    try {
      await portalDb.addBookmark(session.jwt, questionId, note);
      setSaved((prev) => new Set(prev).add(questionId));
    } catch (err) {
      setError(describeError(err));
    }
  }

  async function remove(id: string) {
    if (!window.confirm('Remove this question from history?')) return;
    try {
      await portalDb.deleteQuestion(session.jwt, id);
      setRows((prev) => prev.filter((r) => r.id !== id));
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <h1>{isAdmin ? 'Question history' : 'My question history'}</h1>
        {isAdmin && (
          <label className="inline-label">
            Analyst
            <select
              value={analystFilter}
              onChange={(e) => setAnalystFilter(e.target.value)}
              aria-label="Analyst"
            >
              <option value="">All analysts</option>
              {directory.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.display_name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      <ErrorBanner message={downloadError} onDismiss={clearError} />

      {loading ? (
        <p className="muted">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="muted">No questions yet. Head to Ask to get started.</p>
      ) : (
        <ul className="history-list">
          {rows.map((r) => (
            <li key={r.id}>
              <button
                type="button"
                className={`history-row ${openId === r.id ? 'open' : ''}`}
                onClick={() => setOpenId(openId === r.id ? null : r.id)}
                aria-expanded={openId === r.id}
              >
                <span className={`dot dot-${r.status}`} aria-label={r.status} />
                <span className="history-question">{r.question}</span>
                <span className="muted small">
                  {isAdmin ? `${nameOf(r.analyst_id)} · ` : ''}
                  {formatDateTime(r.created_at)}
                </span>
              </button>
              {openId === r.id && (
                <AnswerCard
                  record={r}
                  ownerOf={ownerOf}
                  onDownload={download}
                  askedBy={isAdmin ? nameOf(r.analyst_id) : undefined}
                  actions={
                    <>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => navigate(`/ask?q=${encodeURIComponent(r.question)}`)}
                      >
                        Ask again
                      </button>
                      {r.status === 'answered' && (
                        <BookmarkButton
                          bookmarked={saved.has(r.id)}
                          onBookmark={(note) => bookmark(r.id, note)}
                        />
                      )}
                      {(isAdmin || r.analyst_id === session.analystId) && (
                        <button type="button" className="link-button danger" onClick={() => remove(r.id)}>
                          Remove
                        </button>
                      )}
                    </>
                  }
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
