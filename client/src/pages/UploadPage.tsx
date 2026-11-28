import { useEffect, useRef, useState } from 'react';
import * as kd from '../api/knowledgeDrop';
import * as portalDb from '../api/portalDb';
import { useAuth, useSession } from '../auth/AuthContext';
import { KdTokenNotice, StatusBadge } from '../components/common';
import { describeError } from '../lib/errors';
import { applyStatuses, isPending, uploadErrorMessage } from '../lib/uploadQueue';
import type { UploadItem } from '../lib/uploadQueue';

export const POLL_MS = 3000;

let nextId = 0;

export function UploadPage() {
  const session = useSession();
  const { kdToken } = useAuth();
  const [items, setItems] = useState<UploadItem[]>([]);
  const [dragging, setDragging] = useState(false);
  const busy = useRef(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const update = (id: string, patch: Partial<UploadItem>) =>
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, ...patch } : it)));

  // Upload queued files one at a time.
  useEffect(() => {
    if (busy.current || !kdToken) return;
    const next = items.find((i) => i.state === 'queued');
    if (!next) return;
    busy.current = true;
    update(next.id, { state: 'uploading' });

    void (async () => {
      try {
        const res = await kd.uploadDocument(kdToken, next.file);
        // The service replaces same-name documents, so retire the old owner row first.
        try {
          await portalDb.markOwnerDeleted(session.jwt, next.file.name, session.analystId);
          await portalDb.recordOwner(session.jwt, res.documentId, next.file.name);
        } catch (err) {
          update(next.id, {
            message: `Uploaded, but the owner could not be recorded: ${describeError(err)}`,
          });
        }
        update(next.id, {
          documentId: res.documentId,
          state: res.status === 'processing' ? 'processing' : res.status,
        });
      } catch (err) {
        const message =
          err instanceof kd.KdError ? uploadErrorMessage(err.status, describeError(err)) : describeError(err);
        update(next.id, { state: 'error', message });
      } finally {
        busy.current = false;
        setItems((prev) => [...prev]); // re-run this effect for the next queued file
      }
    })();
  }, [items, kdToken, session.jwt, session.analystId]);

  // Poll the library until every uploaded file has finished ingesting.
  const anyProcessing = items.some((i) => i.state === 'processing');
  useEffect(() => {
    if (!anyProcessing || !kdToken) return;
    const timer = setInterval(async () => {
      try {
        const docs = await kd.listDocuments(kdToken, { limit: 1000 });
        setItems((prev) => applyStatuses(prev, docs));
      } catch {
        // transient; try again next tick
      }
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [anyProcessing, kdToken]);

  function addFiles(files: FileList | File[]) {
    const added = Array.from(files).map<UploadItem>((file) => ({
      id: `u${nextId++}`,
      file,
      state: 'queued',
    }));
    setItems((prev) => [...prev, ...added]);
  }

  return (
    <div className="page">
      <h1>Upload documents</h1>
      <p className="muted">
        Add policies, SOPs and reference guides. Everyone in the portal can search them, and you are recorded
        as the owner. Uploading a file with an existing name replaces that document.
      </p>
      <KdTokenNotice />

      <div
        className={`dropzone ${dragging ? 'dragging' : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
        }}
      >
        <p>Drag files here, or</p>
        <button type="button" onClick={() => inputRef.current?.click()} disabled={!kdToken}>
          Choose files
        </button>
        <input
          ref={inputRef}
          type="file"
          multiple
          hidden
          data-testid="file-input"
          onChange={(e) => {
            if (e.target.files) addFiles(e.target.files);
            e.target.value = '';
          }}
        />
        <p className="muted small">PDF, Word, text and Markdown files work best.</p>
      </div>

      {items.length > 0 && (
        <div className="card table-card">
          <table>
            <thead>
              <tr>
                <th>File</th>
                <th>Size</th>
                <th>Status</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {items.map((it) => (
                <tr key={it.id}>
                  <td className="strong">{it.file.name}</td>
                  <td className="small muted">{Math.max(1, Math.round(it.file.size / 1024))} KB</td>
                  <td>
                    <StatusBadge status={it.state} />
                  </td>
                  <td className="small">{it.message ?? (isPending(it) ? 'Working…' : '')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
