import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const bundled = await build({
  entryPoints: [fileURLToPath(new URL('../src/reader/search.ts', import.meta.url))],
  bundle: true, write: false, format: 'esm', platform: 'browser',
});
const { searchCatalog } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`);

test('search delegates query and pagination to the catalog API', async () => {
  const requests = [];
  const result = await searchCatalog('Franken', 2, undefined, async (url, init) => {
    requests.push({ url, init });
    return {
      ok: true,
      status: 200,
      json: async () => ({
        items: [{ id: 'book-1', canonicalTitle: 'Frankenstein', originalLanguage: 'en', authors: ['Mary Shelley'], firstPublicationYear: 1818 }],
        totalItems: 21,
        totalPages: 2,
        page: 1,
      }),
    };
  });

  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, '/api/v1/books?q=Franken&page=2&size=20');
  assert.equal(requests[0].init.credentials, 'same-origin');
  assert.equal(result.totalItems, 21);
  assert.equal(result.books[0].title, 'Frankenstein');
  assert.equal(result.books[0].author, 'Mary Shelley');
  assert.equal('publicationYear' in result.books[0], false);
});

test('category and author filters are combined with free text and resettable independently', async () => {
  let requested;
  await searchCatalog('Harbour', 0, undefined, async url => {
    requested = new URL(url, 'https://bookrush.test');
    return {ok:true,json:async()=>({items:[],totalItems:0,totalPages:0,page:0})};
  }, {category:' Adventure ',author:' Fixture Writer ',year:'1912'});
  assert.equal(requested.searchParams.get('q'), 'Harbour');
  assert.equal(requested.searchParams.get('category'), 'Adventure');
  assert.equal(requested.searchParams.get('author'), 'Fixture Writer');
  assert.equal(requested.searchParams.has('year'), false);
});
