import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, filterQuery, request } from './client.js';

const response = (value, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => value,
});
afterEach(() => vi.unstubAllGlobals());

describe('cookie session API client', () => {
  it('obtains a CSRF token and includes cookies on mutations', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(response({ token: 'csrf-test', headerName: 'X-XSRF-TOKEN' }))
      .mockResolvedValueOnce(response({ id: 'tx1' }, 201));
    vi.stubGlobal('fetch', fetch);
    const result = await request('/transfers', {
      method: 'POST',
      body: { amountMinor: 101 },
      headers: { 'Idempotency-Key': 'request-id' },
    });
    expect(result.id).toBe('tx1');
    expect(fetch.mock.calls[0][0]).toBe('/api/v1/auth/csrf');
    expect(fetch.mock.calls[1][1]).toMatchObject({
      credentials: 'include',
      method: 'POST',
      body: '{"amountMinor":101}',
      headers: { 'X-XSRF-TOKEN': 'csrf-test', 'Idempotency-Key': 'request-id' },
    });
    expect(fetch.mock.calls[1][1].headers.Authorization).toBeUndefined();
  });
  it('preserves useful problem details without exposing raw response data', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          response({ detail: 'Insufficient balance', code: 'INSUFFICIENT_FUNDS' }, 422),
        ),
    );
    await expect(request('/wallet')).rejects.toMatchObject({
      name: 'ApiError',
      message: 'Insufficient balance',
      code: 'INSUFFICIENT_FUNDS',
      status: 422,
    });
  });
  it('does not send an unsafe request if CSRF retrieval fails', async () => {
    const fetch = vi.fn().mockResolvedValue(response({ detail: 'Unavailable' }, 503));
    vi.stubGlobal('fetch', fetch);
    await expect(request('/auth/logout', { method: 'POST' })).rejects.toBeInstanceOf(ApiError);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('expires the UI session on an unauthorized protected response', async () => {
    const listener = vi.fn();
    window.addEventListener('walletpay:session-expired', listener);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ detail: 'Sign in again' }, 401)));
    await expect(request('/wallet')).rejects.toMatchObject({ status: 401 });
    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener('walletpay:session-expired', listener);
  });
  it('handles no-content logout responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response({ token: 'token' }))
        .mockResolvedValueOnce(response(null, 204)),
    );
    expect(await request('/auth/logout', { method: 'POST' })).toBeUndefined();
  });
  it('encodes filters and excludes empty/ALL values', () => {
    expect(filterQuery({ page: 0, search: 'a & b', type: 'ALL', from: '' })).toBe(
      '?page=0&search=a+%26+b',
    );
  });
});
