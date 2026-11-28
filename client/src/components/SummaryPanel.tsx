import { useCallback, useEffect, useRef, useState } from 'react';
import * as kd from '../api/knowledgeDrop';
import * as portalDb from '../api/portalDb';
import type { DocumentSummary, SummaryLength } from '../api/types';
import { useAuth, useSession } from '../auth/AuthContext';
import { describeSummaryError } from '../lib/errors';
import { formatShort } from '../lib/format';

interface SummaryPanelProps {
  doc: DocumentSummary;
  ownerName: string;
  /** Display name for an analyst id (who generated a cached summary). */
  nameOf: (analystId: string | null | undefined) => string;
  onClose: () => void;
  onDownload: (filename: string) => void;
}

interface Shown {
  summary: string;
  chunksUsed: number;
  /** Set when the summary came from the portal's cache. */
  cachedBy?: string | null;
  cachedAt?: string;
}

const LENGTHS: { value: SummaryLength; label: string }[] = [
  { value: 'short', label: 'Short' },
  { value: 'detailed', label: 'Detailed' },
];

/** Side panel on the Library page: shows a cached summary or asks knowledgeDrop for one. */
export function SummaryPanel({ doc, ownerName, nameOf, onClose, onDownload }: SummaryPanelProps) {
  const session = useSession();
  const { kdToken } = useAuth();
  const [length, setLength] = useState<SummaryLength>('short');
  const [shown, setShown] = useState<Shown | null>(null);
  const [phase, setPhase] = useState<'idle' | 'checking' | 'generating'>('idle');
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  // Ignore responses for a document or length that is no longer selected.
  const current = useRef('');

  const generate = useCallback(
    async (key: string) => {
      if (!kdToken) {
        setError('The portal is not connected to knowledgeDrop yet.');
        return;
      }
      setPhase('generating');
      setError(null);
      setNote(null);
      try {
        const res = await kd.summarize(kdToken, doc.documentId, length);
        if (current.current !== key) return;
        setShown({ summary: res.summary, chunksUsed: res.chunksUsed });
        try {
          await portalDb.saveSummary(session.jwt, {
            document_id: doc.documentId,
            length,
            summary: res.summary,
            chunks_used: res.chunksUsed,
            created_by: session.analystId,
          });
        } catch {
          if (current.current === key) setNote('The summary could not be saved for others to reuse.');
        }
      } catch (err) {
        if (current.current === key) setError(describeSummaryError(err));
      } finally {
        if (current.current === key) setPhase('idle');
      }
    },
    [kdToken, doc.documentId, length, session.jwt, session.analystId],
  );

  useEffect(() => {
    const key = `${doc.documentId}:${length}`;
    current.current = key;
    setShown(null);
    setError(null);
    setNote(null);
    setPhase('checking');
    void (async () => {
      let cached = null;
      try {
        cached = await portalDb.getSummary(session.jwt, doc.documentId, length);
      } catch {
        // A cache miss or error just means we ask the service.
      }
      if (current.current !== key) return;
      if (cached) {
        setShown({
          summary: cached.summary,
          chunksUsed: cached.chunks_used,
          cachedBy: cached.created_by,
          cachedAt: cached.created_at,
        });
        setPhase('idle');
      } else {
        await generate(key);
      }
    })();
    // generate changes with length/doc, which this effect already tracks.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doc.documentId, length, session.jwt]);

  const busy = phase !== 'idle';

  return (
    <aside className="card summary-panel" aria-label={`Summary of ${doc.filename}`}>
      <header className="summary-head">
        <div>
          <h2 className="summary-title">{doc.filename}</h2>
          <p className="muted small">
            Uploaded by {ownerName} · {formatShort(doc.uploadTime)}
          </p>
        </div>
        <button
          type="button"
          className="link-button close-button"
          onClick={onClose}
          aria-label="Close summary"
        >
          ×
        </button>
      </header>

      <div className="summary-controls">
        <div className="segmented" role="group" aria-label="Summary length">
          {LENGTHS.map((l) => (
            <button
              key={l.value}
              type="button"
              className={length === l.value ? 'active' : ''}
              aria-pressed={length === l.value}
              onClick={() => setLength(l.value)}
            >
              {l.label}
            </button>
          ))}
        </div>
        <button type="button" className="secondary" onClick={() => onDownload(doc.filename)}>
          Download source
        </button>
      </div>

      {phase === 'checking' && <p className="muted">Loading…</p>}
      {phase === 'generating' && (
        <p className="muted">Summarizing… this can take a few seconds for long documents.</p>
      )}
      {error && (
        <p className="answer-error" role="alert">
          {error}
        </p>
      )}

      {shown && (
        <>
          <p className="summary-text">{shown.summary}</p>
          <p className="muted small summary-meta">
            {shown.cachedAt
              ? `Saved summary · generated by ${nameOf(shown.cachedBy)} on ${formatShort(shown.cachedAt)}`
              : 'Just generated'}
            {shown.chunksUsed > 0 && ` · from ${shown.chunksUsed} sections`}
          </p>
          {note && <p className="muted small">{note}</p>}
          {shown.cachedAt && (
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() => void generate(current.current)}
            >
              Regenerate
            </button>
          )}
        </>
      )}
    </aside>
  );
}
