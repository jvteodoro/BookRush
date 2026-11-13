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
    if (url === '/api/v1/books/outside-feed') return response({ canonicalTitle: 'Livro salvo', authors: ['Machado de Assis'], firstPublicationYear: 1899, originalLanguage: 'pt', thumbnailUrl: '/cover.png' });
    if (url === '/api/v1/books/recent-only') return response({ canonicalTitle: 'Livro recente', originalLanguage: 'pt', thumbnailUrl: '/cover.png' });
    return response({ canonicalTitle: 'Livro inesperado' });
  };
  const result = await loadWithTransport(transport);
  assert.deepEqual(result.saved, ['outside-feed']);
  assert.deepEqual(result.recent, ['recent-only']);
  assert.equal(result.books[0].title, 'Livro salvo');
  assert.equal(result.books[0].author, 'Machado de Assis');
  assert.equal(result.books[0].publicationYear, 1899);
  assert.equal(result.books[1].author, 'Autor não informado');
  assert.equal(result.books[1].publicationYear, null);
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

test('library and progress stay authenticated while metadata uses the public catalog', async () => {
  const authenticated = [];
  const controller = new globalThis.AbortController();
  const result = await loadLibrary(controller.signal, async url => {
    authenticated.push(url);
    assert.ok(url.startsWith('/api/v1/reader/'));
    if (url === '/api/v1/reader/library') return response([{ book_id: 'saved-book' }]);
    if (url === '/api/v1/reader/recent') return response([]);
    if (url === '/api/v1/reader/books/saved-book/progress') return response([{ percent: 23 }]);
    assert.fail(`Unexpected authenticated request: ${url}`);
  }, async (url, init) => {
    assert.equal(url, '/api/v1/books/saved-book');
    assert.equal(init.signal, controller.signal);
    assert.equal(init.headers, undefined);
    return response({ canonicalTitle: 'Livro acessível', originalLanguage: 'pt' });
  });
  assert.equal(result.books[0].available, true);
  assert.equal(result.books[0].title, 'Livro acessível');
  assert.equal(result.books[0].progress, 23);
  assert.ok(authenticated.includes('/api/v1/reader/books/saved-book/progress'));
});

test('a saved book beyond the first100 catalog entries keeps its metadata through the public ID lookup', async () => {
  const id = 'f0fe263e-a019-417c-8ee0-bea610b59497';
  const title = 'The Extraordinary Adventures of Arsène Lupin, Gentleman-Burglar';
  const publicRequests = [];
  const result = await loadLibrary(undefined, async url => {
    if (url === '/api/v1/reader/library') return response([{ book_id: id }]);
    if (url === '/api/v1/reader/recent' || url.endsWith('/progress')) return response([]);
    return response({ bookId: id, status: 'CONTENT_NOT_AVAILABLE' });
  }, async (url, init) => {
    publicRequests.push(url);
    assert.equal(init.credentials, 'same-origin');
    assert.equal(init.headers, undefined, 'Product bearer must not be sent to the public catalog');
    return response({ id, canonicalTitle: title, authors: ['Leblanc, Maurice'],
      thumbnailUrl: `/api/v1/books/${id}/thumbnail`, status: 'REVIEW_REQUIRED',
      categories: [{ id: 'category', name: 'Crime Fiction', scheme: 'GUTENBERG' }] });
  });
  assert.equal(result.books[0].available, true);
  assert.equal(result.books[0].title, title);
  assert.equal(result.books[0].author, 'Leblanc, Maurice');
  assert.equal(result.books[0].coverUrl, `/api/v1/books/${id}/thumbnail`);
  assert.equal(result.books[0].genre, 'Crime Fiction');
  assert.deepEqual(publicRequests, [`/api/v1/books/${id}`]);
  assert.deepEqual(result.warnings, []);
});
