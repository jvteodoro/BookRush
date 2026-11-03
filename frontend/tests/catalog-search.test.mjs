import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const result = await build({ entryPoints: [fileURLToPath(new URL('../src/reader/search.ts', import.meta.url))], bundle: true, write: false, platform: 'browser', format: 'esm' });
const { searchCatalog } = await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
const response = (items, metadata = {}) => ({
  ok: true,
  json: async () => ({ items, totalItems: items.length, totalPages: 1, page: 0, ...metadata }),
});

test('search delegates the normalized query to the catalog endpoint', async () => {
  const urls = [];
  const transport = async (url, init) => {
    urls.push(url);
    assert.equal(init.credentials, 'same-origin');
    const query = new URL(url, 'http://localhost').searchParams.get('q');
    return response(query === 'memorias'
      ? [{ id: '1', canonicalTitle: 'Memórias', description: 'Uma história no oceano' }]
      : []);
  };
  assert.equal((await searchCatalog('memorias', 0, undefined, transport)).books[0].id, '1');
  assert.equal((await searchCatalog('  zzzz  ', 0, undefined, transport)).totalItems, 0);
  assert.deepEqual(urls, [
    '/api/v1/books?q=memorias&page=0&size=20',
    '/api/v1/books?q=zzzz&page=0&size=20',
  ]);
});

test('search preserves server pagination metadata and maps the requested page', async () => {
  const transport = async url => {
    const page = Number(new URL(url, 'http://localhost').searchParams.get('page'));
    return response(
      Array.from({ length: page ? 5 : 20 }, (_, index) => ({ id: `${page}-${index}`, canonicalTitle: `Livro ${index}` })),
      { totalItems: 25, totalPages: 2, page },
    );
  };
  const result = await searchCatalog('livro', 1, undefined, transport);
  assert.equal(result.totalItems, 25);
  assert.equal(result.books.length, 5);
  assert.equal(result.books[0].id, '1-0');
});

test('failed catalog loads can be retried instead of caching an empty result', async () => {
  let calls = 0;
  const transport = async () => {
    calls++;
    return calls === 1 ? { ok: false } : response([{ id: '1', canonicalTitle: 'Livro' }]);
  };
  await assert.rejects(searchCatalog('livro', 0, undefined, transport), /Não foi possível/);
  assert.equal((await searchCatalog('livro', 0, undefined, transport)).totalItems, 1);
});
