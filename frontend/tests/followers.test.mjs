import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const bundle = await build({
  entryPoints: [fileURLToPath(new URL('../src/reader/followers.ts', import.meta.url))],
  bundle: true, write: false, platform: 'browser', format: 'esm',
  plugins: [{ name: 'followers-auth', setup(builder) {
    builder.onResolve({ filter: /auth$/ }, () => ({ path: 'auth', namespace: 'test' }));
    builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({
      contents: 'export const authClient = { fetch() { throw new Error("Unexpected live request"); } };',
    }));
  } }],
});
const { loadFollowerStats } = await import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`);
const response = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });

test('own profile uses authenticated subject and handles zero, singular and formatted plural', async () => {
  for (const [count, label] of [[0, '0 seguidores'], [1, '1 seguidor'], [1234, '1.234 seguidores']]) {
    const result = await loadFollowerStats(undefined, undefined, async (url) => {
      assert.equal(url, '/api/v1/social/followers');
      return response({ visible: true, following: false, followerCount: count });
    });
    assert.equal(result.label, label);
    assert.equal(result.followerCount, count);
  }
});

test('other profiles encode subjects and preserve authoritative follow state', async () => {
  const stats = await loadFollowerStats('reader/id', undefined, async (url) => {
    assert.equal(url, '/api/v1/social/followers?user=reader%2Fid');
    return response({ visible: true, following: true, followerCount: 2 });
  });
  assert.equal(stats.following, true);
  assert.equal(stats.followerCount, 2);
});

test('private counts never turn into a visible zero or leak a number', async () => {
  const stats = await loadFollowerStats('private-reader', undefined,
    async () => response({ visible: false, following: false, followerCount: 99 }));
  assert.equal(stats.label, 'Seguidores privados');
  assert.equal(stats.followerCount, null);
});

test('failed or malformed responses remain errors instead of fabricated counts', async () => {
  await assert.rejects(loadFollowerStats(undefined, undefined, async () => response(null, 401)), /sessão expirou/);
  await assert.rejects(loadFollowerStats(undefined, undefined, async () => response(null, 500)), /Não foi possível/);
  for (const value of [-1, '3', undefined]) {
    await assert.rejects(loadFollowerStats(undefined, undefined,
      async () => response({ visible: true, following: false, followerCount: value })), /Não foi possível/);
  }
});
