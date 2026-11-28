// Thin typed wrapper around the existing knowledgeDrop REST endpoints. Every call
// goes to the same origin (/knowledgeDrop/...), which Vite (dev) or nginx (server)
// proxies to the service.
import type {
  AskResponse,
  DocumentStatus,
  DocumentStatusResponse,
  DocumentSummary,
  HealthResponse,
  RegisterClientResponse,
  SummarizeResponse,
  SummaryLength,
} from './types';

const BASE = '/knowledgeDrop';

/** Error from the service, parsed from its `{ error, reason }` body. */
export class KdError extends Error {
  readonly status: number;
  readonly error: string;
  readonly reason: string;

  constructor(status: number, error: string, reason: string) {
    super(reason || error || `HTTP ${status}`);
    this.name = 'KdError';
    this.status = status;
    this.error = error;
    this.reason = reason;
  }
}

async function toKdError(res: Response): Promise<KdError> {
  let error = res.statusText || 'Error';
  let reason = '';
  try {
    const body = (await res.json()) as { error?: string; reason?: string };
    error = body.error ?? error;
    reason = body.reason ?? '';
  } catch {
    // Non-JSON error body (e.g. a proxy page); keep the status text.
  }
  return new KdError(res.status, error, reason);
}

function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

async function requestJson<T>(path: string, init: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, init);
  if (!res.ok) {
    throw await toKdError(res);
  }
  return (await res.json()) as T;
}

export function ask(token: string, question: string, k: number): Promise<AskResponse> {
  return requestJson<AskResponse>('/ask', {
    method: 'POST',
    headers: { ...authHeaders(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, k }),
  });
}

/** Summarizes one ready document with the service's LLM (POST /knowledgeDrop/summarize). */
export function summarize(
  token: string,
  documentId: string,
  length: SummaryLength,
): Promise<SummarizeResponse> {
  return requestJson<SummarizeResponse>('/summarize', {
    method: 'POST',
    headers: { ...authHeaders(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ documentId, length }),
  });
}

export interface ListOptions {
  status?: DocumentStatus;
  limit?: number;
  offset?: number;
}

export function listDocuments(token: string, opts: ListOptions = {}): Promise<DocumentSummary[]> {
  const params = new URLSearchParams();
  if (opts.status) params.set('status', opts.status);
  if (opts.limit !== undefined) params.set('limit', String(opts.limit));
  if (opts.offset !== undefined) params.set('offset', String(opts.offset));
  const qs = params.toString();
  return requestJson<DocumentSummary[]>(`/document/list${qs ? `?${qs}` : ''}`, {
    headers: authHeaders(token),
  });
}

export function uploadDocument(token: string, file: File): Promise<DocumentStatusResponse> {
  const form = new FormData();
  form.append('file', file, file.name);
  return requestJson<DocumentStatusResponse>('/document/create', {
    method: 'POST',
    headers: authHeaders(token),
    body: form,
  });
}

/** Downloads a document's original bytes by name (the service looks up by name). */
export async function readDocument(token: string, documentName: string): Promise<Blob> {
  const res = await fetch(`${BASE}/document/read?documentName=${encodeURIComponent(documentName)}`, {
    headers: authHeaders(token),
  });
  if (!res.ok) {
    throw await toKdError(res);
  }
  return res.blob();
}

export interface DeleteResult {
  /**
   * True when the service answered 501: it soft-deletes the document and commits
   * before reporting that search-index cleanup is not implemented yet.
   */
  pendingCleanup: boolean;
}

export async function deleteDocument(token: string, documentName: string): Promise<DeleteResult> {
  const res = await fetch(`${BASE}/document/delete?documentName=${encodeURIComponent(documentName)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
  if (res.ok) {
    return { pendingCleanup: false };
  }
  if (res.status === 501) {
    return { pendingCleanup: true };
  }
  throw await toKdError(res);
}

export function registerClient(name: string): Promise<RegisterClientResponse> {
  return requestJson<RegisterClientResponse>('/client/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
}

export function health(): Promise<HealthResponse> {
  return requestJson<HealthResponse>('/health', {});
}

/** Saves a Blob through a temporary object URL. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
