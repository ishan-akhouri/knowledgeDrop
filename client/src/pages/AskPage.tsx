import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import * as kd from '../api/knowledgeDrop';
import * as portalDb from '../api/portalDb';
import type { NewQuestion, QuestionRecord } from '../api/types';
import { useAuth, useSession } from '../auth/AuthContext';
import { AnswerCard, BookmarkButton } from '../components/AnswerCard';
import { ErrorBanner, KdTokenNotice } from '../components/common';
import { describeAskError, describeError } from '../lib/errors';
import { useDownload } from '../lib/useDownload';
import { useOwnerNames } from '../lib/useOwnerNames';

export const SUGGESTED_QUESTIONS = [
  'What are the submission requirements for 340B claims?',
  'How do we handle a prior authorization appeal?',
  'Explain coordination of benefits when a member has two plans.',
  'When should a member call be escalated to a supervisor?',
  'What does denial code CO-197 mean and how do we resolve it?',
];

export function AskPage() {
  const session = useSession();
  const { kdToken } = useAuth();
  const [params] = useSearchParams();
  const [question, setQuestion] = useState(params.get('q') ?? '');
  const [k, setK] = useState(5);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<QuestionRecord | null>(null);
  const [bookmarked, setBookmarked] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { ownerOf, reload: reloadOwners } = useOwnerNames();
  const { download, error: downloadError, clearError } = useDownload();

  useEffect(() => {
    const q = params.get('q');
    if (q) setQuestion(q);
  }, [params]);

  async function submit(text: string) {
    const trimmed = text.trim();
    if (!trimmed || !kdToken) return;
    setLoading(true);
    setError(null);
    setResult(null);
    setBookmarked(false);

    let entry: NewQuestion;
    try {
      const res = await kd.ask(kdToken, trimmed, k);
      entry = {
        question: trimmed,
        k,
        answer: res.answer,
        citations: res.citations ?? [],
        status: 'answered',
        error: null,
      };
      void reloadOwners();
    } catch (err) {
      entry = {
        question: trimmed,
        k,
        answer: null,
        citations: [],
        status: 'error',
        error: describeAskError(err),
      };
    }

    try {
      setResult(await portalDb.insertQuestion(session.jwt, entry));
    } catch (err) {
      // Still show the answer even if history could not be saved.
      setResult({ ...entry, id: '', analyst_id: session.analystId, created_at: new Date().toISOString() });
      setError(`The answer could not be saved to your history: ${describeError(err)}`);
    } finally {
      setLoading(false);
    }
  }

  async function bookmark(note: string) {
    if (!result?.id) return;
    try {
      await portalDb.addBookmark(session.jwt, result.id, note);
      setBookmarked(true);
    } catch (err) {
      setError(`Could not save the bookmark: ${describeError(err)}`);
    }
  }

  return (
    <div className="page">
      <h1>Ask the knowledge base</h1>
      <p className="muted">
        Ask about policies, claims handling and procedures. Answers come only from internal documents ingested
        into knowledgeDrop, with sources you can open.
      </p>

      <KdTokenNotice />

      <form
        className="card ask-form"
        onSubmit={(e) => {
          e.preventDefault();
          void submit(question);
        }}
      >
        <label htmlFor="question" className="label">
          Your question
        </label>
        <textarea
          id="question"
          rows={3}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
              e.preventDefault();
              void submit(question);
            }
          }}
          placeholder="e.g. Can you tell me about 340B claims?"
        />
        <div className="ask-controls">
          <label className="inline-label">
            Sources to use
            <select value={k} onChange={(e) => setK(Number(e.target.value))} aria-label="Sources to use">
              {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={loading || !question.trim() || !kdToken}>
            {loading ? 'Searching…' : 'Ask'}
          </button>
        </div>
        <div className="suggestions" aria-label="Suggested questions">
          {SUGGESTED_QUESTIONS.map((s) => (
            <button key={s} type="button" className="chip" onClick={() => setQuestion(s)}>
              {s}
            </button>
          ))}
        </div>
      </form>

      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      <ErrorBanner message={downloadError} onDismiss={clearError} />

      {loading && <p className="muted">Searching internal documentation…</p>}

      {result && (
        <AnswerCard
          record={result}
          ownerOf={ownerOf}
          onDownload={download}
          actions={
            result.id && result.status === 'answered' ? (
              <BookmarkButton bookmarked={bookmarked} onBookmark={bookmark} />
            ) : undefined
          }
        />
      )}
    </div>
  );
}
