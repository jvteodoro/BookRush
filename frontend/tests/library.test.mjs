import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const bundled = await build({
  entryPoints: [fileURLToPath(new URL('../src/reader/library.ts', import.meta.url))],
  bundle: true, write: false, format: 'esm', platform: 'browser',
  plugins: [{ name: 'auth-test', setup(builder) {
    builder.onResolve({ filter: /auth$/ }, () => ({ path: 'auth', namespace: 'test' }));
    builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({ contents: 'export const authClient = { fetch() { throw new Error("Unexpected live request"); } };' }));
  } }],
});
const { loadLibrary } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`);
const response = (data, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => data });
const loadWithTransport = transport => loadLibrary(undefined, transport, transport);

test('saved books outside the feed are resolved by ID with real progress; duplicate IDs are fetched once', async () => {
  const requests = [];
  const transport = async url => {
    requests.push(url);
    if (url === '/api/v1/reader/library') return response([{ book_id: 'outside-feed' }, { bookId: 'outside-feed' }]);
    if (url === '/api/v1/reader/recent') return response([{ book_id: 'recent-only' }]);
    if (url.endsWith('/progress')) return response([{ percent: url.includes('outside-feed') ? 37 : 8 }]);
    return response({ canonicalTitle: url.endsWith('outside-feed') ? 'Livro salvo' : 'Livro recente', originalLanguage: 'pt', thumbnailUrl: '/cover.png' });
  };
  const result = await loadWithTransport(transport);
  assert.deepEqual(result.saved, ['outside-feed']);
  assert.deepEqual(result.recent, ['recent-only']);
  assert.equal(result.books[0].title, 'Livro salvo');
  assert.equal(result.books[0].progress, 37);
  assert.equal(result.books[0].available, true);
  assert.equal(requests.filter(url => url === '/api/v1/books/outside-feed').length, 1);
  assert.deepEqual(result.warnings, []);
});

test('an unavailable saved book retains its ID for removal; optional recent failure does not discard the library', async () => {
  const result = await loadWithTransport(async url => {
    if (url === '/api/v1/reader/library') return response([{ book_id: 'removed-from-catalog' }]);
    return response(null, 404);
  });
  assert.deepEqual(result.saved, ['removed-from-catalog']);
  assert.equal(result.books[0].id, 'removed-from-catalog');
  assert.equal(result.books[0].available, false);
  assert.equal(result.warnings.length, 2);
});

test('an expired session is an error, not an empty successful library', async () => {
  await assert.rejects(loadWithTransport(async () => response(null, 401)), /sessão expirou/);
});

test('metadata requests use bounded concurrency for a large saved library', async () => {
  let concurrent = 0;
  let maximum = 0;
  const result = await loadWithTransport(async url => {
    if (url === '/api/v1/reader/library') return response(Array.from({ length: 20 }, (_, index) => ({ book_id: `book-${index}` })));
    if (url === '/api/v1/reader/recent' || url.endsWith('/progress')) return response([]);
    concurrent++; maximum = Math.max(maximum, concurrent);
    await new Promise(resolve => globalThis.setTimeout(resolve, 1));
    concurrent--;
    return response({ canonicalTitle: url });
  });
  assert.equal(result.books.length, 20);
  assert.ok(maximum <= 6);
});

test('catalog metadata is fetched without the reader bearer while library and progress stay authenticated', async () => {
  const previousFetch = globalThis.fetch;
  const publicRequests = [];
  const protectedRequests = [];
  globalThis.fetch = async (url, init) => {
    publicRequests.push(url);
    assert.equal(init.headers?.Authorization, undefined);
    assert.equal(init.credentials, 'same-origin');
    assert.equal(url, '/api/v1/books/saved-book');
    return response({ canonicalTitle: 'Livro acessível', originalLanguage: 'pt' });
  };
  try {
    const result = await loadLibrary(undefined, async url => {
      protectedRequests.push(url);
      assert.ok(url.startsWith('/api/v1/reader/'), 'Product token must never be sent to catalog');
      return response(url.endsWith('/library') ? [{ book_id: 'saved-book' }] : []);
    });
    assert.equal(result.books[0].available, true);
    assert.equal(result.books[0].title, 'Livro acessível');
    assert.deepEqual(publicRequests, ['/api/v1/books/saved-book']);
    assert.ok(protectedRequests.includes('/api/v1/reader/books/saved-book/progress'));
  } finally { globalThis.fetch = previousFetch; }
});
