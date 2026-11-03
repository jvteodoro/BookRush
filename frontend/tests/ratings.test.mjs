import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import { build } from 'esbuild';

async function load(entry, hooks = false) {
  const bundle = await build({ entryPoints: [fileURLToPath(new URL(entry, import.meta.url))], bundle: true,
    write: false, platform: 'browser', format: 'esm', jsx: 'automatic',
    plugins: [{ name: 'ratings-test', setup(builder) {
      builder.onResolve({ filter: /auth$/ }, () => ({ path: 'auth', namespace: 'test' }));
      builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({ contents: 'export const authClient = { fetch(...args) { return globalThis.__ratingFetch(...args); } };' }));
      if (hooks) {
        builder.onResolve({ filter: /\/Drawer$/ }, () => ({ path: 'drawer', namespace: 'drawer' }));
        builder.onLoad({ filter: /.*/, namespace: 'drawer' }, () => ({ contents: 'export const Drawer = props => props.children;' }));
        builder.onResolve({ filter: /^react$/ }, args => args.importer.includes('/node_modules/') ? undefined : ({ path: 'react', namespace: 'hooks' }));
        builder.onLoad({ filter: /.*/, namespace: 'hooks' }, () => ({ contents: `
          export const forwardRef = fn => fn;
          export const useRef = () => ({ current: false });
          export function useState() { const index = globalThis.__ratingIndex++; return [globalThis.__ratingStates[index], value => globalThis.__ratingUpdates.push([index, value])]; }
          export function useEffect(effect) { globalThis.__ratingEffect = effect; }
        ` }));
      }
    } }],
  });
  return import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`);
}
const { loadRatings, saveRating, removeRating, ratingLabel } = await load('../src/reader/ratings.ts');
const response = (payload, status = 200) => ({ ok: status < 300, status, json: async () => payload });
const summary = { bookId: 'book', totalRatings: 2, averageRating: 3.5, myRating: 5 };

test('ratings use authenticated transport, own score only and server aggregate', async () => {
  const calls = [];
  globalThis.__ratingFetch = async (...args) => { calls.push(args); return response(summary); };
  const signal = new globalThis.AbortController().signal;
  assert.deepEqual(await loadRatings('book', signal), summary);
  assert.equal(calls[0][0], '/api/v1/social/books/book/ratings');
  assert.equal(calls[0][1].signal, signal);
  assert.deepEqual(await saveRating('book', 5), summary);
  assert.equal(calls[1][1].method, 'PUT');
  assert.deepEqual(JSON.parse(calls[1][1].body), { stars: 5 });
  assert.deepEqual(await removeRating('book'), summary);
  assert.equal(calls[2][1].method, 'DELETE');
});

test('invalid stars are rejected before any request and HTTP failures stay recoverable', async () => {
  globalThis.__ratingFetch = () => { throw Error('Unexpected request'); };
  for (const stars of [0, 6, 1.5, NaN, Infinity]) await assert.rejects(saveRating('book', stars), /1 a 5/);
  for (const [status, text] of [[401, /sessão expirou/], [403, /permissão/], [404, /não está mais disponível/], [503, /Tente novamente/]]) {
    await assert.rejects(loadRatings('book', undefined, async () => response({}, status)), text);
  }
  assert.match(ratingLabel({ totalRatings: 0 }), /Ainda não há/);
  assert.match(ratingLabel(summary), /3,5 de 5 estrelas · 2 avaliações/);
  assert.match(ratingLabel({ totalRatings: 1, averageRating: 5 }), /1 avaliação$/);
});

const { BookDetailsDrawer } = await load('../src/reader/BookDetailsDrawer.tsx', true);
function nodes(tree) {
  if (!tree || typeof tree !== 'object') return [];
  return [tree, ...[tree.props?.children].flat(Infinity).flatMap(nodes)];
}
function render({ data = summary, loading = false, error = '', selected = 4 } = {}) {
  globalThis.__ratingStates = [data, loading, error, 0, selected, false, ''];
  globalThis.__ratingUpdates = []; globalThis.__ratingIndex = 0;
  return nodes(BookDetailsDrawer({ book: { id: 'book', title: 'Book', author: 'Author' }, onClose() {} }));
}
const settle = () => new Promise(resolve => globalThis.setTimeout(resolve, 0));

test('drawer exposes keyboard radios, aggregate, own score and removal', () => {
  const tree = render();
  assert.equal(tree.filter(node => node.type === 'input' && node.props.type === 'radio').length, 5);
  assert.ok(tree.some(node => node.props?.children === 'Sua avaliação atual: 5 de 5 estrelas.'));
  tree.find(node => node.props?.['aria-label'] === '2 estrelas').props.onChange();
  assert.deepEqual(globalThis.__ratingUpdates, [[4, 2]]);
  assert.ok(tree.some(node => node.props?.children === 'Remover minha avaliação'));
  assert.ok(!render({ data: { ...summary, myRating: null } }).some(node => node.props?.children === 'Remover minha avaliação'));
});

test('saving waits for server confirmation, prevents duplicate submissions and preserves data on failure', async () => {
  let finish;
  let requests = 0;
  globalThis.__ratingFetch = async () => { requests++; return new Promise(resolve => { finish = resolve; }); };
  const tree = render();
  const save = tree.find(node => node.props?.children === 'Salvar avaliação');
  save.props.onClick(); save.props.onClick();
  assert.equal(requests, 1);
  assert.ok(!globalThis.__ratingUpdates.some(([index]) => index === 0));
  finish(response({ ...summary, myRating: 4 })); await settle();
  assert.deepEqual(globalThis.__ratingUpdates.find(([index]) => index === 0)[1], { ...summary, myRating: 4 });
  globalThis.__ratingFetch = async () => response({}, 503);
  render().find(node => node.props?.children === 'Salvar avaliação').props.onClick(); await settle();
  assert.ok(globalThis.__ratingUpdates.some(([index, value]) => index === 2 && /Tente novamente/.test(value)));
  assert.ok(!globalThis.__ratingUpdates.some(([index]) => index === 0));
});

test('removal uses DELETE and initial failures expose retry', async () => {
  globalThis.__ratingFetch = async (_url, init) => { assert.equal(init.method, 'DELETE'); return response({ bookId: 'book', totalRatings: 0 }); };
  render().find(node => node.props?.children === 'Remover minha avaliação').props.onClick(); await settle();
  assert.deepEqual(globalThis.__ratingUpdates.find(([index]) => index === 0)[1], { bookId: 'book', totalRatings: 0 });
  const failed = render({ data: null, error: 'Unavailable' });
  assert.ok(failed.some(node => node.props?.role === 'alert'));
  failed.find(node => node.props?.children === 'Tentar novamente').props.onClick();
  assert.equal(globalThis.__ratingUpdates[0][0], 3);
});

test('closing drawer aborts load and ignores late results', async () => {
  let finish; let signal;
  globalThis.__ratingFetch = async (_url, init) => { signal = init.signal; return new Promise(resolve => { finish = resolve; }); };
  render({ data: null, loading: true });
  const cleanup = globalThis.__ratingEffect();
  cleanup(); assert.equal(signal.aborted, true);
  const before = globalThis.__ratingUpdates.length;
  finish(response(summary)); await settle();
  assert.equal(globalThis.__ratingUpdates.length, before);
});
