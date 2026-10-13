import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const bundled = await build({
  entryPoints: [fileURLToPath(new URL('../src/reader/comments.ts', import.meta.url))],
  bundle: true, write: false, platform: 'browser', format: 'esm',
  plugins: [{ name: 'comments-test-auth', setup(builder) {
    builder.onResolve({ filter: /auth$/ }, () => ({ path: 'auth', namespace: 'test' }));
    builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({ contents: 'export const authClient = { fetch() { throw new Error("Unexpected live request"); } };' }));
  } }],
});
const { loadComments, postComment } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`);
const response = (data, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => data });

test('comment loading preserves author identity and text returned by the server', async () => {
  const rows = [{ id: 'comment-1', subject_key: 'issuer|subject', authorName: 'Ana', body: 'Texto com\nmais de uma linha.' }];
  const loaded = await loadComments('book/id', undefined, async url => {
    assert.equal(url, '/api/v1/social/books/book%2Fid/comments');
    return response(rows);
  });
  assert.deepEqual(loaded, rows);
});

test('publishing sends the trimmed body and returns the confirmed server ID', async () => {
  const posted = await postComment('book-id', '  Gostei desta leitura!  ', async (url, init) => {
    assert.equal(url, '/api/v1/social/books/book-id/comments');
    assert.equal(init.method, 'POST');
    assert.equal(init.headers['Content-Type'], 'application/json');
    assert.deepEqual(JSON.parse(init.body), { body: 'Gostei desta leitura!' });
    return response({ id: 'server-id', body: 'Gostei desta leitura!' });
  });
  assert.equal(posted.id, 'server-id');
});

test('blank and oversized comments are rejected before any request', async () => {
  let requests = 0;
  const transport = async () => { requests++; return response({}); };
  await assert.rejects(postComment('book-id', '   ', transport), /2000 caracteres/);
  await assert.rejects(postComment('book-id', 'x'.repeat(2001), transport), /2000 caracteres/);
  assert.equal(requests, 0);
});

test('failed publication is an error rather than a fabricated successful comment', async () => {
  await assert.rejects(postComment('book-id', 'Meu comentário', async () => response(null, 500)), /texto foi preservado/);
});

test('an expired session is distinguished from an empty conversation', async () => {
  await assert.rejects(loadComments('book-id', undefined, async () => response(null, 401)), /sessão expirou/);
});
