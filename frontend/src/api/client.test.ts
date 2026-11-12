import { describe, expect, it, vi } from 'vitest';
import { ApiError, apiGet } from './client';

describe('apiGet', () => {
  it('prefixes the API base path and returns parsed JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiGet<{ ok: boolean }>('/system/status')).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/system/status', expect.objectContaining({
      headers: { Accept: 'application/json' },
    }));
  });

  it('throws ApiError carrying the problem detail', async () => {
    const problem = { title: 'Not Found', status: 404, detail: 'No team with id 99' };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(problem, { status: 404 })));

    const error = await apiGet('/teams/99').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, message: 'No team with id 99', problem });
  });

  it('falls back to the HTTP status when the error body is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>bad gateway</html>', { status: 502 })));

    await expect(apiGet('/system/status')).rejects.toMatchObject({ status: 502, message: 'HTTP 502' });
  });
});
