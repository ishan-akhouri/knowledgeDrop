import { useState } from 'react';
import type { ReactNode } from 'react';
import type { Citation, QuestionRecord } from '../api/types';
import { formatDateTime } from '../lib/format';

interface CitationListProps {
  citations: Citation[];
  ownerOf: (documentId: string) => string;
  onDownload?: (filename: string) => void;
}

export function CitationList({ citations, ownerOf, onDownload }: CitationListProps) {
  if (citations.length === 0) return null;
  return (
    <ol className="citations">
      {citations.map((c, i) => (
        <li key={`${c.documentId}-${i}`} className="citation">
          <div className="citation-head">
            <span className="citation-file">{c.filename}</span>
            {c.page != null && <span className="muted"> · page {c.page}</span>}
            <span className="muted"> · uploaded by {ownerOf(c.documentId)}</span>
            {onDownload && (
              <button type="button" className="link-button" onClick={() => onDownload(c.filename)}>
                Download source
              </button>
            )}
          </div>
          <blockquote className="snippet">{c.snippet}</blockquote>
        </li>
      ))}
    </ol>
  );
}

interface AnswerCardProps {
  record: QuestionRecord;
  ownerOf: (documentId: string) => string;
  onDownload?: (filename: string) => void;
  askedBy?: string;
  actions?: ReactNode;
}

export function AnswerCard({ record, ownerOf, onDownload, askedBy, actions }: AnswerCardProps) {
  const noSources = record.status === 'answered' && record.citations.length === 0;
  return (
    <article className="card answer-card">
      <header className="answer-head">
        <h3 className="answer-question">{record.question}</h3>
        <span className="muted small">
          {askedBy ? `${askedBy} · ` : ''}
          {formatDateTime(record.created_at)} · top {record.k}
        </span>
      </header>

      {record.status === 'error' ? (
        <p className="answer-error">{record.error}</p>
      ) : (
        <>
          <p className="answer-text">{record.answer}</p>
          {noSources ? (
            <p className="muted">No relevant internal documentation found.</p>
          ) : (
            <>
              <h4 className="sources-title">Sources</h4>
              <CitationList citations={record.citations} ownerOf={ownerOf} onDownload={onDownload} />
            </>
          )}
        </>
      )}

      {actions && <footer className="answer-actions">{actions}</footer>}
    </article>
  );
}

interface BookmarkButtonProps {
  bookmarked: boolean;
  onBookmark: (note: string) => Promise<void>;
}

export function BookmarkButton({ bookmarked, onBookmark }: BookmarkButtonProps) {
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  if (bookmarked) return <span className="muted small">★ Saved</span>;

  if (!open) {
    return (
      <button type="button" className="secondary" onClick={() => setOpen(true)}>
        ☆ Save answer
      </button>
    );
  }

  return (
    <form
      className="inline-form"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        try {
          await onBookmark(note.trim());
          setOpen(false);
        } finally {
          setBusy(false);
        }
      }}
    >
      <input
        aria-label="Bookmark note"
        placeholder="Optional note, e.g. 'use for 340B escalations'"
        value={note}
        onChange={(e) => setNote(e.target.value)}
      />
      <button type="submit" disabled={busy}>
        Save
      </button>
      <button type="button" className="link-button" onClick={() => setOpen(false)}>
        Cancel
      </button>
    </form>
  );
}
