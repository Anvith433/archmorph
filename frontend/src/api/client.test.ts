import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, projectPath } from './client';

const ID = '3f1c2b9a-1d2e-4f50-8a6b-7c8d9e0f1a2b';

function respond(status: number, body: unknown, headers: Record<string, string> = {}) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })));
}

afterEach(() => vi.unstubAllGlobals());

describe('api client', () => {
  it('unwraps the data of a successful envelope', async () => {
    respond(200, { success: true, message: 'ok', data: { projectId: ID, name: 'demo' }, timestamp: '' });
    await expect(api.project(ID)).resolves.toMatchObject({ name: 'demo' });
  });

  it('maps an error envelope to ApiError with code, hint and request id', async () => {
    respond(422, { success: false, message: 'No Java sources found.', errorCode: 'INVALID_PROJECT', hint: 'Zip the project root.', requestId: 'r-1', timestamp: '' });
    const error = await api.project(ID).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ message: 'No Java sources found.', code: 'INVALID_PROJECT', hint: 'Zip the project root.', requestId: 'r-1', status: 422 });
  });

  it('turns a 429 without a body into a rate-limit error with Retry-After hint', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('nope', { status: 429, headers: { 'Retry-After': '30' } })));
    await expect(api.project(ID)).rejects.toMatchObject({ code: 'RATE_LIMITED', hint: 'Retry in 30 seconds.' });
  });

  it('reports an unreachable server as a network error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
    await expect(api.project(ID)).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 });
  });

  it('never interpolates malformed identifiers into URLs', async () => {
    expect(() => projectPath('../../etc/passwd')).toThrow(ApiError);
    await expect(api.diff(ID, '../x')).rejects.toMatchObject({ code: 'INVALID_REQUEST' });
    await expect(api.job('not-a-uuid')).rejects.toMatchObject({ code: 'INVALID_REQUEST' });
    expect(projectPath(ID, '/plan')).toBe(`/api/v1/projects/${ID}/plan`);
  });
});
