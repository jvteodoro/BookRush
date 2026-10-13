import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import process from 'node:process';
import { build } from 'esbuild';

const result = await build({ entryPoints: [fileURLToPath(new URL('../src/reader/search.ts', import.meta.url))], bundle: true, write: false, platform: 'browser', format: 'esm' });
const { searchCatalog } = await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
const response = items => ({ ok: true, json: async () => ({ items, totalPages: 1 }) });

test('search matches real titles/descriptions without accents and never calls the failing filtered endpoint', async () => {
  const urls = [];
  const transport = async (url, init) => {
    urls.push(url); assert.equal(init.headers, undefined);
    return response([{ id: '1', canonicalTitle: 'Memórias', description: 'Uma história no oceano' }, { id: '2', canonicalTitle: 'Alice' }]);
  };
  assert.equal((await searchCatalog('memorias', 0, undefined, transport)).books[0].id, '1');
  assert.equal((await searchCatalog('oceano', 0, undefined, transport)).books[0].id, '1');
  assert.equal((await searchCatalog('zzzz', 0, undefined, transport)).totalItems, 0);
  assert.deepEqual(urls, ['/api/v1/books?page=0&size=100']);
});

test('search reads all catalog pages and paginates matches beyond the initial feed', async () => {
  const transport = async url => {
    const page = Number(new URL(url, 'http://localhost').searchParams.get('page'));
    return { ok: true, json: async () => ({ totalPages: 2, items: Array.from({ length: page ? 5 : 20 }, (_, index) => ({ id: `${page}-${index}`, canonicalTitle: `Livro ${index}` })) }) };
  };
  const result = await searchCatalog('livro', 1, undefined, transport);
  assert.equal(result.totalItems, 25);
  assert.equal(result.books.length, 5);
  assert.equal(result.books[0].id, '1-0');
});

test('failed catalog loads can be retried instead of caching an empty result', async () => {
  let calls = 0;
  const transport = async () => { calls++; return calls === 1 ? { ok: false } : response([{ id: '1', canonicalTitle: 'Livro' }]); };
  await assert.rejects(searchCatalog('livro', 0, undefined, transport), /Não foi possível/);
  assert.equal((await searchCatalog('livro', 0, undefined, transport)).totalItems, 1);
});

test('search through the local proxy finds a title from the published catalog', { skip: process.env.BOOKRUSH_TEST_SEARCH_LIVE !== '1' }, async () => {
  const transport = (url, init) => globalThis.fetch(new URL(url, 'http://localhost:18080'), init);
  const catalog = await searchCatalog('', 0, undefined, transport);
  assert.ok(catalog.books.length > 0);
  const match = await searchCatalog(catalog.books[0].title, 0, undefined, transport);
  assert.ok(match.books.some(book => book.id === catalog.books[0].id));
});
