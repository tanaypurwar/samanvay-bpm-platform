import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, setUnauthorizedHandler } from './client';

function mockFetch(status: number, body?: unknown) {
  // a fresh Response per call: a body can only be read once
  const fetchMock = vi.fn().mockImplementation(async () =>
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  setUnauthorizedHandler(null);
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

describe('api client', () => {
  it('sends the CSRF cookie value as X-XSRF-TOKEN on writes only', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const fetchMock = mockFetch(200, { ok: true });

    await api('/api/tasks/1/claim', { method: 'POST' });
    await api('/api/tasks');

    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('abc123');
    expect(fetchMock.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('normalizes backend errors including field errors', async () => {
    mockFetch(422, {
      code: 'VALIDATION_FAILED',
      message: 'Please correct the highlighted fields.',
      correlationId: 'c0ffee00-1111',
      fieldErrors: [{ field: 'comment', message: 'required' }],
    });

    const error = (await api('/api/tasks/1/complete', { method: 'POST', body: {} }).catch((e) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(422);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.fieldErrors).toEqual([{ field: 'comment', message: 'required' }]);
  });

  it('reports an expired session once per 401 unless silenced', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    mockFetch(401, { code: 'UNAUTHENTICATED', message: 'Please sign in.' });

    await api('/api/tasks').catch(() => undefined);
    await api('/api/auth/me', { silentUnauthorized: true }).catch(() => undefined);

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('returns undefined for 204 responses', async () => {
    mockFetch(204);
    await expect(api('/api/tasks/1/complete', { method: 'POST' })).resolves.toBeUndefined();
  });
});
