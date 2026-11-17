import assert from 'node:assert/strict';
import { test } from 'node:test';
import { build } from 'esbuild';
import { fileURLToPath, URL } from 'node:url';
import { Buffer } from 'node:buffer';

const bundle = await build({
  stdin: {
    contents: `
      import React from 'react';
      import { renderToStaticMarkup } from 'react-dom/server.browser';
      import { RecommendationsPage } from './src/reader/RecommendationsPage';
      import { bookMetadata } from './src/reader/bookMetadata';
      export function render(catalogBook) {
        const book = { id: 'fixture', title: 'Fixture title', ...bookMetadata(catalogBook),
          genre: 'Fiction', progress: 0, minutes: 0, description: '' };
        return renderToStaticMarkup(React.createElement(RecommendationsPage, {
          books: [book], saved: [], pending: [], message: '',
          onSave() {}, onOpen() {}, onLucky() {}, onSearch() {},
        }));
      }
    `,
    resolveDir: fileURLToPath(new URL('..', import.meta.url)),
    sourcefile: 'recommendation-year.tsx',
  },
  bundle: true, write: false, platform: 'browser', format: 'esm',
  jsx: 'automatic', preserveSymlinks: true,
  plugins: [{ name: 'no-network', setup(builder) {
    builder.onResolve({ filter: /\/auth$/ }, () => ({ path: 'auth', namespace: 'test' }));
    builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({
      contents: 'export const authClient = { imageUrl() { throw new Error("Unexpected network call"); } };',
    }));
  } }],
});
const { render } = await import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`);

test('recommendations render the bibliographic edition year when first publication is unknown', () => {
  const html = render({ authors: ['Fixture Author'], firstPublicationYear: null, publicationYear: 1899 });
  assert.match(html, /br-book-card__year[^>]*>1899</);
  assert.match(html, /Fixture Author/);
});

test('recommendations still prefer the known first publication over a later edition', () => {
  const html = render({ firstPublicationYear: 1818, publicationYear: 1912 });
  assert.match(html, /br-book-card__year[^>]*>1818</);
  assert.doesNotMatch(html, />1912</);
});

test('recommendations omit unknown years instead of rendering a digital release date', () => {
  const html = render({ firstPublicationYear: null, publicationYear: null, digitalReleaseDate: '2008-08-26' });
  assert.doesNotMatch(html, /br-book-card__year/);
  assert.doesNotMatch(html, /2008/);
});
