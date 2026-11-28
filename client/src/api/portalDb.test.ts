import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../test/helpers';
import * as portalDb from './portalDb';

afterEach(() => portalDb.setUnauthorizedHandler(null));

describe('portalDb client', () => {
  it('login calls the login RPC without a JWT', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ token: 'jwt' }));

    await expect(portalDb.login('dana', 'pw')).resolves.toBe('jwt');

    const [url, init] = fetchSpy.mock.calls[0];
    expect(url).toBe('/db/rpc/login');
    expect((init?.headers as Record<string, string>).Authorization).toBeUndefined();
    expect(JSON.parse(init?.body as string)).toEqual({ username: 'dana', password: 'pw' });
  });

  it('surfaces the PostgREST error message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ code: '42501', message: 'Invalid username or password' }, 401),
    );

    await expect(portalDb.login('dana', 'bad')).rejects.toMatchObject({
      name: 'DbError',
      code: '42501',
      message: 'Invalid username or password',
    });
  });

  it('calls the unauthorized handler when a signed-in request gets 401', async () => {
    const handler = vi.fn();
    portalDb.setUnauthorizedHandler(handler);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ code: 'PGRST301', message: 'JWT expired' }, 401),
    );

    await expect(portalDb.listQuestions('expired')).rejects.toBeInstanceOf(portalDb.DbError);
    expect(handler).toHaveBeenCalledOnce();
  });

  it('insertQuestion asks for the created row back', async () => {
    const row = { id: 'q1' };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([row], 201));

    await expect(
      portalDb.insertQuestion('jwt', {
        question: 'q',
        k: 5,
        answer: null,
        citations: [],
        status: 'error',
        error: 'x',
      }),
    ).resolves.toEqual(row);

    const headers = fetchSpy.mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers.Prefer).toBe('return=representation');
    expect(headers.Authorization).toBe('Bearer jwt');
  });

  it('markOwnerDeleted only touches live rows for that filename', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));

    await portalDb.markOwnerDeleted('jwt', '340b policy.md', 'a1');

    const [url, init] = fetchSpy.mock.calls[0];
    expect(url).toBe('/db/document_owners?filename=eq.340b%20policy.md&deleted_at=is.null');
    expect(init?.method).toBe('PATCH');
    expect(JSON.parse(init?.body as string)).toMatchObject({ deleted_by: 'a1' });
  });
});
