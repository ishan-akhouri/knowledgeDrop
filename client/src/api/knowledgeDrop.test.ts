import { describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../test/helpers';
import * as kd from './knowledgeDrop';

function mockFetch(response: Response) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(response);
}

describe('knowledgeDrop client', () => {
  it('ask posts the question with the bearer token', async () => {
    const fetchSpy = mockFetch(jsonResponse({ answer: 'A', citations: [] }));

    const res = await kd.ask('tok', 'What is 340B?', 3);

    expect(res.answer).toBe('A');
    const [url, init] = fetchSpy.mock.calls[0];
    expect(url).toBe('/knowledgeDrop/ask');
    expect(init?.method).toBe('POST');
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer tok');
    expect(JSON.parse(init?.body as string)).toEqual({ question: 'What is 340B?', k: 3 });
  });

  it('turns the {error, reason} body into a KdError', async () => {
    mockFetch(jsonResponse({ error: 'Not Implemented', reason: 'ask is not implemented' }, 501));

    const err = await kd.ask('tok', 'q', 5).catch((e: unknown) => e);

    expect(err).toBeInstanceOf(kd.KdError);
    expect(err).toMatchObject({ status: 501, error: 'Not Implemented', reason: 'ask is not implemented' });
  });

  it('keeps the status text when the error body is not JSON', async () => {
    mockFetch(new Response('<html>bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }));

    const err = await kd.listDocuments('tok').catch((e: unknown) => e);

    expect(err).toMatchObject({ status: 502, error: 'Bad Gateway' });
  });

  it('listDocuments builds the query string', async () => {
    const fetchSpy = mockFetch(jsonResponse([]));

    await kd.listDocuments('tok', { status: 'ready', limit: 25, offset: 50 });

    expect(fetchSpy.mock.calls[0][0]).toBe('/knowledgeDrop/document/list?status=ready&limit=25&offset=50');
  });

  it('uploadDocument sends a multipart body with a "file" part', async () => {
    const fetchSpy = mockFetch(jsonResponse({ documentId: 'd1', status: 'processing' }, 202));
    const file = new File(['hello'], 'policy.txt', { type: 'text/plain' });

    const res = await kd.uploadDocument('tok', file);

    expect(res).toEqual({ documentId: 'd1', status: 'processing' });
    const body = fetchSpy.mock.calls[0][1]?.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect((body.get('file') as File).name).toBe('policy.txt');
  });

  it('deleteDocument treats 501 as deleted with cleanup pending', async () => {
    mockFetch(jsonResponse({ error: 'Not Implemented', reason: 'chunk deletion' }, 501));
    await expect(kd.deleteDocument('tok', 'a b.pdf')).resolves.toEqual({ pendingCleanup: true });
  });

  it('deleteDocument encodes the name and throws on 404', async () => {
    const fetchSpy = mockFetch(jsonResponse({ error: 'Not Found', reason: 'no such document' }, 404));

    await expect(kd.deleteDocument('tok', 'a b.pdf')).rejects.toMatchObject({ status: 404 });
    expect(fetchSpy.mock.calls[0][0]).toBe('/knowledgeDrop/document/delete?documentName=a%20b.pdf');
  });

  it('registerClient sends no Authorization header', async () => {
    const fetchSpy = mockFetch(jsonResponse({ clientId: 'c', clientToken: 't' }, 201));

    await kd.registerClient('Harborline Health');

    const headers = fetchSpy.mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
  });
});
