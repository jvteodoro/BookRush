import assert from 'node:assert/strict';
import { test } from 'node:test';
import { build } from 'esbuild';
import { URL, fileURLToPath } from 'node:url';
import { Buffer } from 'node:buffer';

const bundled = await build({
  entryPoints: [fileURLToPath(new URL('../src/reader/bookMetadata.ts', import.meta.url))],
  bundle: true, write: false, format: 'esm', platform: 'browser',
});
const { bookMetadata } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`);

test('authors retain credit order and omit duplicate or blank names', () => {
  assert.deepEqual(bookMetadata({ authors: [' Mary Shelley ', '', 'Coautor', 'Mary Shelley'], firstPublicationYear: 1818 }),
    { author: 'Mary Shelley · Coautor', firstPublicationYear: 1818 });
});

test('legacy and incomplete metadata never invent an author or publication year', () => {
  for (const book of [{}, { authors: [], firstPublicationYear: null }, { authors: [' '], firstPublicationYear: 1.5 }]) {
    assert.deepEqual(bookMetadata(book), { author: 'Autor não informado', firstPublicationYear: null });
  }
});
