import { describe, expect, it } from 'vitest';
import { KdError } from '../api/knowledgeDrop';
import type { DocumentSummary } from '../api/types';
import { isExpired, sessionFromJwt } from '../auth/session';
import { describeAskError } from './errors';
import { applyStatuses, uploadErrorMessage } from './uploadQueue';
import type { UploadItem } from './uploadQueue';

function b64url(obj: unknown): string {
  return btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

describe('describeAskError', () => {
  it('explains that Ask is not implemented on 501', () => {
    expect(describeAskError(new KdError(501, 'Not Implemented', ''))).toMatch(/isn't available/);
  });
  it('points to Setup on 401', () => {
    expect(describeAskError(new KdError(401, 'Unauthorized', ''))).toMatch(/Setup/);
  });
  it('includes the reason on 400', () => {
    expect(describeAskError(new KdError(400, 'Bad Request', 'question must not be blank'))).toMatch(
      /question must not be blank/,
    );
  });
  it('explains network failures', () => {
    expect(describeAskError(new TypeError('Failed to fetch'))).toMatch(/Could not reach/);
  });
});

describe('applyStatuses', () => {
  const file = new File(['x'], 'a.txt');
  const doc = (documentId: string, status: DocumentSummary['status']): DocumentSummary => ({
    documentId,
    filename: 'a.txt',
    type: 'text/plain',
    uploadTime: '2026-01-01T00:00:00Z',
    status,
  });

  it('moves processing items to ready or failed', () => {
    const items: UploadItem[] = [
      { id: '1', file, state: 'processing', documentId: 'd1' },
      { id: '2', file, state: 'processing', documentId: 'd2' },
      { id: '3', file, state: 'processing', documentId: 'd3' },
    ];
    const next = applyStatuses(items, [doc('d1', 'ready'), doc('d2', 'failed'), doc('d3', 'processing')]);
    expect(next.map((i) => i.state)).toEqual(['ready', 'failed', 'processing']);
    expect(next[1].message).toMatch(/failed/);
  });

  it('leaves items that are not in the list or not processing alone', () => {
    const items: UploadItem[] = [
      { id: '1', file, state: 'processing', documentId: 'missing' },
      { id: '2', file, state: 'error', message: 'boom' },
    ];
    expect(applyStatuses(items, [])).toEqual(items);
  });

  it('maps 413 and 415 to friendly messages', () => {
    expect(uploadErrorMessage(413, 'x')).toMatch(/too large/);
    expect(uploadErrorMessage(415, 'x')).toMatch(/not supported/);
    expect(uploadErrorMessage(500, 'fallback')).toBe('fallback');
  });
});

describe('session', () => {
  it('reads role and analyst from the JWT claims', () => {
    const jwt = `${b64url({ alg: 'HS256' })}.${b64url({
      role: 'portal_admin',
      sub: 'a1',
      name: 'Ada Admin',
      exp: 2_000_000_000,
    })}.sig`;
    expect(sessionFromJwt(jwt)).toMatchObject({ analystId: 'a1', role: 'admin', name: 'Ada Admin' });
  });

  it('maps portal_user to the user role and detects expiry', () => {
    const jwt = `${b64url({})}.${b64url({ role: 'portal_user', sub: 'u1', exp: 100 })}.sig`;
    const s = sessionFromJwt(jwt);
    expect(s.role).toBe('user');
    expect(isExpired(s, 200_000)).toBe(true);
  });

  it('rejects malformed tokens', () => {
    expect(() => sessionFromJwt('nope')).toThrow();
  });
});
