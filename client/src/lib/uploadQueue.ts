import type { DocumentSummary } from '../api/types';

export type UploadState = 'queued' | 'uploading' | 'processing' | 'ready' | 'failed' | 'error';

export interface UploadItem {
  id: string;
  file: File;
  state: UploadState;
  documentId?: string;
  message?: string;
}

export function isPending(item: UploadItem): boolean {
  return item.state === 'queued' || item.state === 'uploading' || item.state === 'processing';
}

/**
 * Applies the latest /document/list results to items still being ingested.
 * Items whose document is not in the list yet keep their state.
 */
export function applyStatuses(items: UploadItem[], docs: DocumentSummary[]): UploadItem[] {
  const byId = new Map(docs.map((d) => [d.documentId, d]));
  return items.map((item) => {
    if (item.state !== 'processing' || !item.documentId) return item;
    const doc = byId.get(item.documentId);
    if (!doc || doc.status === 'processing') return item;
    return {
      ...item,
      state: doc.status,
      message: doc.status === 'failed' ? 'Ingestion failed on the server.' : undefined,
    };
  });
}

export function uploadErrorMessage(status: number, fallback: string): string {
  if (status === 413) return 'File is too large for the server.';
  if (status === 415) return 'This file type is not supported.';
  return fallback;
}
