import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';

async function load(entry, hooks = false) {
  const result = await build({ entryPoints: [fileURLToPath(new URL(entry, import.meta.url))], bundle: true,
    write: false, platform: 'browser', format: 'esm', jsx: 'automatic', preserveSymlinks: true,
    plugins: [{ name: 'metadata-test', setup(builder) {
      builder.onResolve({ filter: /auth$/ }, () => ({ path: 'auth', namespace: 'auth' }));
      builder.onLoad({ filter: /.*/, namespace: 'auth' }, () => ({ contents: 'export const authClient={fetch(...args){return globalThis.__metadataFetch(...args);}};' }));
      if (hooks) {
        builder.onResolve({ filter: /^react$/ }, args => args.importer.includes('/node_modules/') ? undefined : ({ path: 'react', namespace: 'hooks' }));
        builder.onLoad({ filter: /.*/, namespace: 'hooks' }, () => ({ contents: `
          export function useState(initial){const index=globalThis.__stateIndex++;return [globalThis.__states[index]??initial,value=>{const previous=globalThis.__states[index]??initial;globalThis.__states[index]=typeof value==='function'?value(previous):value;}];}
          export function useRef(initial){return {current:initial};}
          export function useEffect(effect){globalThis.__effects.push(effect);}
        ` }));
      }
    } }],
  });
  return import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
}
const { loadSeenBooks, recordOpenedBook, bookOpenedEvent } = await load('../src/reader/seen.ts');
const { categoryLabel, categoryNames } = await load('../src/reader/bookCategories.ts');
const { searchCatalog } = await load('../src/reader/search.ts');
const response = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });
const category = { id: 'category', name: 'Adventure', scheme: 'LCSH' };

test('classification uses real categories and never substitutes a language or guesses a genre', async () => {
  assert.equal(categoryLabel([category, { ...category, scheme: 'GUTENBERG' }]), 'Adventure');
  assert.equal(categoryLabel([]), 'Sem categoria');
  const result = await searchCatalog('', 0, undefined, async () => response({ items: [
    { id: '1', canonicalTitle: 'Classified', originalLanguage: 'en', categories: [category] },
    { id: '2', canonicalTitle: 'Unclassified', originalLanguage: 'pt', categories: [] },
  ], totalItems: 2, totalPages: 1, page: 0 }));
  assert.equal(result.books[0].genre, 'Adventure');
  assert.equal(result.books[0].language, 'en');
  assert.deepEqual(result.books[0].categories, [category]);
  assert.equal(result.books[1].genre, 'Sem categoria');
});

test('technical legacy values are hidden while literary classifications retain readable names', () => {
  const terms = ['application/rdf+xml', 'application/epub+zip', 'image/jpeg', 'text/plain; charset=utf-8', 'Text', 'PR', 'Category: British Literature', 'Art', 'Christmas stories'];
  assert.deepEqual(categoryNames(terms.map(name => ({ id: name, name, scheme: 'GUTENBERG' }))), ['British Literature', 'Art', 'Christmas stories']);
  assert.equal(categoryLabel([{ id: 'technical', name: 'application/rdf+xml', scheme: 'GUTENBERG' }]), 'Sem categoria');
  assert.deepEqual(categoryNames([{ id: 'a', name: 'Category: Novels', scheme: 'GUTENBERG' }, { id: 'b', name: 'Novels', scheme: 'LCSH' }]), ['Novels']);
  assert.deepEqual(categoryNames([{ id: 'code', name: 'PR', scheme: 'LCSH' }, { id: 'type', name: 'Textbooks', scheme: 'GUTENBERG' }]), ['PR', 'Textbooks']);
});

test('seen status batches and deduplicates all requested books without the recent-history limit', async () => {
  const ids = Array.from({ length: 205 }, (_, index) => `book-${index}`);
  const batches = [];
  const signal = new globalThis.AbortController().signal;
  globalThis.__metadataFetch = async (url, init) => {
    assert.equal(init.signal, signal);
    const batch = new URL(url, 'https://app.test').searchParams.get('bookIds').split(',');
    batches.push(batch);
    return response({ items: batch.filter(id => ['book-0', 'book-150', 'book-204'].includes(id)).map(bookId => ({ bookId })) });
  };
  assert.deepEqual([...await loadSeenBooks([...ids, ids[0]], signal)], ['book-0', 'book-150', 'book-204']);
  assert.deepEqual(batches.map(batch => batch.length), [100, 100, 5]);
  assert.equal(batches.flat().length, ids.length);
  assert.equal((await loadSeenBooks([])).size, 0);
});

test('seen errors and aborts are explicit instead of silently converting failures to unseen', async () => {
  for (const [status, message] of [[401, /sessão expirou/], [403, /permissão/], [502, /Tente novamente/]]) {
    await assert.rejects(loadSeenBooks(['book'], undefined, async () => response({}, status)), message);
  }
  const failure = new Error('aborted');
  await assert.rejects(loadSeenBooks(['book'], undefined, async () => { throw failure; }), error => error === failure);
  assert.equal((await loadSeenBooks(['book'], undefined, async () => response({ items: [{ bookId: 'not-requested' }] }))).size, 0);
});

test('opening publishes the visible marker only after authenticated persistence succeeds', async () => {
  const events = [];
  const previousWindow = globalThis.window;
  const previousEvent = globalThis.CustomEvent;
  globalThis.window = { dispatchEvent: event => events.push(event) };
  globalThis.CustomEvent = class { constructor(type, init) { this.type = type; this.detail = init.detail; } };
  try {
    let finish;
    const recording = recordOpenedBook('book', { 'X-BookRush-Impression': 'impression' }, async (url, init) => {
      assert.equal(url, '/api/v1/reader/books/book/opened');
      assert.equal(init.method, 'POST');
      assert.equal(init.headers['X-BookRush-Impression'], 'impression');
      assert.equal(init.body, undefined);
      return new Promise(resolve => { finish = resolve; });
    });
    assert.equal(events.length, 0);
    finish(response({ recorded: true })); await recording;
    assert.equal(events[0].type, bookOpenedEvent); assert.equal(events[0].detail, 'book');
    events.length = 0;
    await assert.rejects(recordOpenedBook('book', {}, async () => response({}, 502)), /não foi possível registrar/);
    assert.equal(events.length, 0);
  } finally { globalThis.window = previousWindow; globalThis.CustomEvent = previousEvent; }
});

const { useSeenBooks } = await load('../src/reader/useSeenBooks.ts', true);
const tick = () => new Promise(resolve => globalThis.setTimeout(resolve, 0));
function hook() {
  globalThis.__states = []; globalThis.__stateIndex = 0; globalThis.__effects = [];
  useSeenBooks(['book']);
  return globalThis.__effects;
}
test('a confirmed opening wins over an older in-flight status response, and closing cancels the read', async () => {
  let listener; let finish; let signal;
  const previousWindow = globalThis.window;
  globalThis.window = { addEventListener: (_name, callback) => { listener = callback; }, removeEventListener() {} };
  globalThis.__metadataFetch = async (_url, init) => { signal = init.signal; return new Promise(resolve => { finish = resolve; }); };
  try {
    const effects = hook(); const stopEvents = effects[0](); const stopLoad = effects[1]();
    listener({ detail: 'book' });
    finish(response({ items: [] })); await tick();
    assert.ok(globalThis.__states[0].has('book'));
    stopLoad(); stopEvents(); assert.equal(signal.aborted, true);
    const second = hook(); second[0](); const abort = second[1](); abort();
    finish(response({ items: [{ bookId: 'book' }] })); await tick();
    assert.equal(globalThis.__states[0], undefined);
  } finally { globalThis.window = previousWindow; }
});

// Exercise the actual legacy reader orchestration: an attempted open is not a
// loaded publication, and a late completion must not mark a different book.
const legacy = await readFile(new URL('../public/pageloop.js', import.meta.url), 'utf8');
const openCode = legacy.slice(legacy.indexOf('async function openBook('), legacy.indexOf('function renderReader('));
const readerCode = legacy.slice(legacy.indexOf('function renderReader('), legacy.indexOf('function renderLibrary('));
function readerSetup() {
  const state = { readerBook: 'book', route: 'reader', readerPage: 0 };
  const host = { isConnected: true, innerHTML: '' };
  let resolveLoad; let rejectLoad; let opens = 0;
  const loaded = new Promise((resolve, reject) => { resolveLoad = resolve; rejectLoad = reject; });
  const context = { state, book: () => ({ id: 'book', title: 'Book' }),
    esc: String, icon: () => '', getMode: () => 'app', shell() {}, render() {}, toast() {},
    document: { getElementById: id => id === 'readium-reader' ? host : null, querySelector: () => null, querySelectorAll: () => [] },
    window: { __BOOKRUSH_API__: { open: async () => { opens++; }, viewable: async () => {}, chapters: async () => [] },
      __BOOKRUSH_READIUM__: { mount: () => loaded, chapters: async () => [] }, __BOOKRUSH_PANELS__: { mount() {} } },
  };
  runInNewContext(`${openCode}\n${readerCode}\nglobalThis.reader=renderReader;globalThis.open=openBook;`, context);
  return { context, state, host, resolveLoad, rejectLoad, opens: () => opens };
}
test('only successfully loaded reading is marked; failed loads and stale completions are not', async () => {
  const good = readerSetup(); await good.context.open('book'); assert.equal(good.opens(), 0);
  good.context.reader(); good.resolveLoad(); await tick(); assert.equal(good.opens(), 1);
  const failed = readerSetup(); failed.context.reader(); failed.rejectLoad(Error('content unavailable')); await tick();
  assert.equal(failed.opens(), 0); assert.match(failed.host.innerHTML, /content unavailable/);
  const stale = readerSetup(); stale.context.reader(); stale.state.readerBook = 'other'; stale.resolveLoad(); await tick();
  assert.equal(stale.opens(), 0);
});
