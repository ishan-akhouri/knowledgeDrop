// Typed wrapper for the portal's own schema (insurance_portal), served by PostgREST
// at /db. Access rules live in the database (row-level security keyed off the JWT),
// so these helpers never filter "for safety": the server already does.
import type {
  Analyst,
  AppRole,
  BookmarkRecord,
  DirectoryEntry,
  DocumentOwner,
  NewQuestion,
  QuestionRecord,
  SettingRecord,
} from './types';

const BASE = '/db';

/** Error from PostgREST (`{ code, message, details, hint }`). */
export class DbError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message || `HTTP ${status}`);
    this.name = 'DbError';
    this.status = status;
    this.code = code;
  }
}

let unauthorizedHandler: (() => void) | null = null;

/** Called whenever PostgREST rejects the JWT (expired, bad signature). */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

interface DbRequest {
  method?: string;
  jwt?: string | null;
  body?: unknown;
  prefer?: string;
}

async function db<T>(path: string, { method = 'GET', jwt, body, prefer }: DbRequest = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (jwt) headers.Authorization = `Bearer ${jwt}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (prefer) headers.Prefer = prefer;

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!res.ok) {
    let code = '';
    let message = res.statusText;
    try {
      const err = (await res.json()) as { code?: string; message?: string };
      code = err.code ?? '';
      message = err.message ?? message;
    } catch {
      // keep status text
    }
    // PGRST301/302/303 = JWT problems; 401 on a signed-in call means the session is gone.
    if (jwt && res.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }
    throw new DbError(res.status, code, message);
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

const enc = encodeURIComponent;

// ---- auth ----

export async function login(username: string, password: string): Promise<string> {
  const result = await db<{ token: string }>('/rpc/login', {
    method: 'POST',
    body: { username, password },
  });
  return result.token;
}

export function changeMyPassword(jwt: string, oldPassword: string, newPassword: string): Promise<void> {
  return db<void>('/rpc/change_my_password', {
    method: 'POST',
    jwt,
    body: { old_password: oldPassword, new_password: newPassword },
  });
}

/** The knowledgeDrop client token, or null if an admin hasn't configured one yet. */
export function fetchKdToken(jwt: string): Promise<string | null> {
  return db<string | null>('/rpc/kd_token', { method: 'POST', jwt, body: {} });
}

// ---- directory / analysts ----

export function listDirectory(jwt: string): Promise<DirectoryEntry[]> {
  return db<DirectoryEntry[]>('/analyst_directory?select=id,display_name,department&order=display_name', {
    jwt,
  });
}

const ANALYST_COLUMNS = 'id,username,display_name,department,role,active,created_at';

export function listAnalysts(jwt: string): Promise<Analyst[]> {
  return db<Analyst[]>(`/analysts?select=${ANALYST_COLUMNS}&order=display_name`, { jwt });
}

export interface NewAnalyst {
  username: string;
  display_name: string;
  department: string;
  role: AppRole;
  password: string;
}

export function createAnalyst(jwt: string, a: NewAnalyst): Promise<string> {
  return db<string>('/rpc/create_analyst', { method: 'POST', jwt, body: a });
}

export function resetPassword(jwt: string, analystId: string, password: string): Promise<void> {
  return db<void>('/rpc/reset_password', {
    method: 'POST',
    jwt,
    body: { analyst_id: analystId, password },
  });
}

export function setActive(jwt: string, analystId: string, active: boolean): Promise<void> {
  return db<void>('/rpc/set_active', { method: 'POST', jwt, body: { analyst_id: analystId, active } });
}

export function updateAnalystRole(jwt: string, analystId: string, role: AppRole): Promise<void> {
  return db<void>(`/analysts?id=eq.${enc(analystId)}`, { method: 'PATCH', jwt, body: { role } });
}

// ---- settings (admin only) ----

export function listSettings(jwt: string): Promise<SettingRecord[]> {
  return db<SettingRecord[]>('/settings?select=key,value,updated_at', { jwt });
}

export function saveSettings(jwt: string, values: Record<string, string>): Promise<void> {
  const now = new Date().toISOString();
  return db<void>('/settings?on_conflict=key', {
    method: 'POST',
    jwt,
    prefer: 'resolution=merge-duplicates,return=minimal',
    body: Object.entries(values).map(([key, value]) => ({ key, value, updated_at: now })),
  });
}

// ---- questions ----

export interface QuestionFilter {
  analystId?: string;
  limit?: number;
}

export function listQuestions(jwt: string, filter: QuestionFilter = {}): Promise<QuestionRecord[]> {
  const params = [`select=*`, `order=created_at.desc`, `limit=${filter.limit ?? 200}`];
  if (filter.analystId) params.push(`analyst_id=eq.${enc(filter.analystId)}`);
  return db<QuestionRecord[]>(`/questions?${params.join('&')}`, { jwt });
}

export async function insertQuestion(jwt: string, q: NewQuestion): Promise<QuestionRecord> {
  const rows = await db<QuestionRecord[]>('/questions', {
    method: 'POST',
    jwt,
    prefer: 'return=representation',
    body: q,
  });
  return rows[0];
}

export function deleteQuestion(jwt: string, id: string): Promise<void> {
  return db<void>(`/questions?id=eq.${enc(id)}`, { method: 'DELETE', jwt });
}

// ---- bookmarks ----

export function listBookmarks(jwt: string): Promise<BookmarkRecord[]> {
  return db<BookmarkRecord[]>('/bookmarks?select=*,question:questions(*)&order=created_at.desc', {
    jwt,
  });
}

export async function addBookmark(jwt: string, questionId: string, note: string): Promise<BookmarkRecord> {
  const rows = await db<BookmarkRecord[]>('/bookmarks', {
    method: 'POST',
    jwt,
    prefer: 'return=representation',
    body: { question_id: questionId, note: note || null },
  });
  return rows[0];
}

export function removeBookmark(jwt: string, id: string): Promise<void> {
  return db<void>(`/bookmarks?id=eq.${enc(id)}`, { method: 'DELETE', jwt });
}

/** Ids of the questions the current analyst has bookmarked. */
export async function myBookmarkedQuestionIds(jwt: string, analystId: string): Promise<Set<string>> {
  const rows = await db<{ question_id: string }[]>(
    `/bookmarks?select=question_id&analyst_id=eq.${enc(analystId)}`,
    { jwt },
  );
  return new Set(rows.map((r) => r.question_id));
}

// ---- document owners ----

export function listOwners(jwt: string): Promise<DocumentOwner[]> {
  return db<DocumentOwner[]>('/document_owners?select=*&order=uploaded_at.desc', { jwt });
}

/**
 * Marks every live owner row for this filename as deleted. Used both on delete and
 * before recording a re-upload, since the service replaces same-name documents.
 */
export function markOwnerDeleted(jwt: string, filename: string, deletedBy: string): Promise<void> {
  return db<void>(`/document_owners?filename=eq.${enc(filename)}&deleted_at=is.null`, {
    method: 'PATCH',
    jwt,
    body: { deleted_at: new Date().toISOString(), deleted_by: deletedBy },
  });
}

export function recordOwner(jwt: string, documentId: string, filename: string): Promise<void> {
  return db<void>('/document_owners', {
    method: 'POST',
    jwt,
    prefer: 'return=minimal',
    body: { document_id: documentId, filename },
  });
}
