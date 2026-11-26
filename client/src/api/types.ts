// Shapes returned by the knowledgeDrop service (mirrors the Java records in
// src/main/java/dev/coms4156/knowledgedrop/api/dto) and by the portal's own schema.

export type DocumentStatus = 'processing' | 'ready' | 'failed';

export interface Citation {
  documentId: string;
  filename: string;
  page: number | null;
  snippet: string;
}

export interface AskResponse {
  answer: string;
  citations: Citation[];
}

export interface DocumentSummary {
  documentId: string;
  filename: string;
  type: string;
  uploadTime: string;
  status: DocumentStatus;
}

export interface DocumentStatusResponse {
  documentId: string;
  status: DocumentStatus;
}

export interface RegisterClientResponse {
  clientId: string;
  clientToken: string;
}

export interface HealthResponse {
  status: string;
  elasticsearch: string;
}

// ---- insurance_portal schema (served by PostgREST) ----

export type AppRole = 'admin' | 'user';

export interface Analyst {
  id: string;
  username: string;
  display_name: string;
  department: string | null;
  role: AppRole;
  active: boolean;
  created_at: string;
}

export interface DirectoryEntry {
  id: string;
  display_name: string;
  department: string | null;
}

export type QuestionStatus = 'answered' | 'error';

export interface QuestionRecord {
  id: string;
  analyst_id: string;
  question: string;
  k: number;
  answer: string | null;
  citations: Citation[];
  status: QuestionStatus;
  error: string | null;
  created_at: string;
}

export interface NewQuestion {
  question: string;
  k: number;
  answer: string | null;
  citations: Citation[];
  status: QuestionStatus;
  error: string | null;
}

export interface BookmarkRecord {
  id: string;
  question_id: string;
  analyst_id: string;
  note: string | null;
  created_at: string;
  question: QuestionRecord | null;
}

export interface DocumentOwner {
  document_id: string;
  filename: string;
  analyst_id: string | null;
  uploaded_at: string;
  deleted_at: string | null;
  deleted_by: string | null;
}

export interface SettingRecord {
  key: string;
  value: string;
  updated_at: string;
}

export const SETTING_KEYS = {
  clientId: 'kd_client_id',
  clientName: 'kd_client_name',
  clientToken: 'kd_client_token',
} as const;
