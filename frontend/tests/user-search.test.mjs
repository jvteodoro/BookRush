import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';
import { build } from 'esbuild';

async function load(entry, hooks = false) {
  const bundle = await build({
    entryPoints: [fileURLToPath(new URL(entry, import.meta.url))], bundle: true,
    write: false, platform: 'browser', format: 'esm', jsx: 'automatic',
    plugins: [{ name: 'user-search-transport', setup(builder) {
      builder.onResolve({ filter: /auth$/ }, () => ({ path: 'auth', namespace: 'test' }));
      builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({ contents: 'export const authClient = { fetch(...args) { return globalThis.__searchFetch(...args); } };' }));
      if (hooks) {
        builder.onResolve({ filter: /^react$/ }, args => args.importer.includes('/node_modules/') ? undefined : ({ path: 'react', namespace: 'hooks' }));
        builder.onLoad({ filter: /.*/, namespace: 'hooks' }, () => ({ contents: `
          export const forwardRef = fn => fn;
          export function useState() { const value = globalThis.__searchStates.shift(); return [value, next => globalThis.__searchUpdates.push(next)]; }
          export function useEffect(effect) { globalThis.__searchEffect = effect; }
        ` }));
      }
    } }],
  });
  return import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`);
}
const { searchUsers } = await load('../src/reader/userSearch.ts');
const response = (payload, status = 200) => ({ ok: status < 300, status, json: async () => payload });

test('user search uses the authenticated client, encodes text and preserves the page', async () => {
  const signal = new globalThis.AbortController().signal;
  const payload = { items: [{ subject: 'opaque/user', displayName: 'Ana', username: 'ana.reader' }], totalItems: 21, totalPages: 2, page: 1 };
  globalThis.__searchFetch = async (url, init) => {
    const params = new URL(url, 'https://app.example').searchParams;
    assert.equal(url.split('?')[0], '/api/v1/profile/search');
    assert.equal(params.get('q'), 'Ana & João_%');
    assert.equal(params.get('page'), '1');
    assert.equal(params.get('size'), '20');
    assert.equal(init.signal, signal);
    return response(payload);
  };
  assert.deepEqual(await searchUsers(' @Ana & João_% ', 1, signal), payload);
});

test('blank user searches do not enumerate profiles or send a request', async () => {
  globalThis.__searchFetch = () => { throw new Error('Unexpected request'); };
  for (const query of ['', '   ', ' @ ']) assert.equal((await searchUsers(query)).items.length, 0);
});

test('user search preserves authentication errors and aborts', async () => {
  for (const [status, text] of [[401, /sessão expirou/], [403, /permissão/], [500, /Não foi possível/]]) {
    await assert.rejects(searchUsers('ana', 0, undefined, async () => response({}, status)), text);
  }
  const failure = new Error('aborted');
  await assert.rejects(searchUsers('ana', 0, undefined, async () => { throw failure; }), error => error === failure);
});

const { UserSearchResults } = await load('../src/reader/UserSearchResults.tsx', true);
function render({ query = 'Ana', data = null, loading = false, error = '', onProfile = () => {} } = {}) {
  globalThis.__searchStates = [0, 0, data, loading, error];
  globalThis.__searchUpdates = [];
  return UserSearchResults({ visible: true, query, onProfile });
}
function nodes(tree) {
  if (!tree || typeof tree !== 'object') return [];
  return [tree, ...[tree.props?.children].flat(Infinity).flatMap(nodes)];
}

test('person results open the selected opaque profile and expose pagination', () => {
  const calls = [];
  const tree = render({ data: { items: [{ subject: 'opaque/user', displayName: 'Ana', username: 'ana.reader' }], totalItems: 21, totalPages: 2, page: 0 }, onProfile: subject => calls.push(subject) });
  const elements = nodes(tree);
  elements.find(node => node.props?.['aria-label'] === 'Ver perfil de Ana').props.onClick();
  assert.deepEqual(calls, ['opaque/user']);
  assert.ok(elements.some(node => node.props?.label === 'Próxima página de pessoas'));
});

test('user results show empty, loading and recoverable error states', () => {
  assert.ok(nodes(render({ query: '' })).some(node => node.props?.children === 'Encontre outros leitores'));
  assert.ok(nodes(render({ loading: true })).some(node => node.props?.role === 'status'));
  const errorTree = nodes(render({ error: 'API offline' }));
  assert.ok(errorTree.some(node => node.props?.role === 'alert'));
  errorTree.find(node => node.props?.children === 'Tentar novamente').props.onClick();
  assert.equal(globalThis.__searchUpdates.length, 1);
  assert.ok(nodes(render({ data: { items: [] } })).some(node => node.props?.children === 'Nenhuma pessoa encontrada'));
});

test('leaving user results aborts an in-flight search and ignores stale completion', async () => {
  let finish;
  let signal;
  globalThis.__searchFetch = async (_url, init) => { signal = init.signal; return new Promise(resolve => { finish = resolve; }); };
  globalThis.window = { setTimeout: callback => { callback(); return 1; }, clearTimeout: () => {} };
  render();
  const cleanup = globalThis.__searchEffect();
  assert.equal(signal.aborted, false);
  cleanup();
  assert.equal(signal.aborted, true);
  const updates = globalThis.__searchUpdates.length;
  finish(response({ items: [] }));
  await new Promise(resolve => globalThis.setTimeout(resolve, 0));
  assert.equal(globalThis.__searchUpdates.length, updates);
});
