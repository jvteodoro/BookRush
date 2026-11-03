import assert from 'node:assert/strict';
import { test } from 'node:test';
import { build } from 'esbuild';
import { Buffer } from 'node:buffer';
import { URL, fileURLToPath } from 'node:url';

// Exercise the real auth module with a deterministic OIDC transport. No
// credentials, network requests or production tokens are used by these tests.
async function loadAuth({ reject = false } = {}) {
  const result = await build({
    entryPoints: [fileURLToPath(new URL('../src/auth.ts', import.meta.url))],
    bundle: true, write: false, format: 'esm', platform: 'browser',
    define: { 'import.meta.env': '{}' },
    plugins: [{ name: 'oidc-test-transport', setup(builder) {
      builder.onResolve({ filter: /^oidc-client-ts$/ }, () => ({ path: 'mock', namespace: 'oidc-test' }));
      builder.onLoad({ filter: /.*/, namespace: 'oidc-test' }, () => ({ contents: `
        export class User {}
        export class WebStorageStateStore {}
        export class UserManager {
          getUser() { return Promise.resolve(null); }
          signinRedirectCallback() {
            window.callbackCalls++;
            return new Promise((resolve, reject) => {
              window.finishCallback = () => ${reject ? 'reject(new Error("Code not valid"))' : 'resolve({ state: { returnTo: "/library" }, access_token: "test-only" })'};
            });
          }
          signinSilentCallback() { window.silentCalls++; return Promise.resolve(); }
        }
      ` }));
    } }],
  });
  globalThis.window = { location: { origin: 'http://localhost:18080', href: 'http://localhost:18080/auth/callback' }, sessionStorage: {}, callbackCalls: 0, silentCalls: 0 };
  const encoded = Buffer.from(result.outputFiles[0].text).toString('base64');
  return (await import(`data:text/javascript;base64,${encoded}#${Math.random()}`)).authClient;
}

test('replayed callback effects share one code exchange and authenticated result', async () => {
  const auth = await loadAuth();
  const first = auth.callback();
  const replay = auth.callback();
  assert.equal(first, replay);
  assert.equal(globalThis.window.callbackCalls, 1);
  globalThis.window.finishCallback();
  const [a, b] = await Promise.all([first, replay]);
  assert.equal(a, b);
  assert.equal(a.returnTo, '/library');
  assert.equal(await auth.currentUser(), a.user);
});

test('a rejected callback is not retried with the same single-use code', async () => {
  const auth = await loadAuth({ reject: true });
  const first = auth.callback();
  const replay = auth.callback();
  const assertions = [assert.rejects(first, /Code not valid/), assert.rejects(replay, /Code not valid/)];
  globalThis.window.finishCallback();
  await Promise.all(assertions);
  await assert.rejects(auth.callback(), /Code not valid/);
  assert.equal(globalThis.window.callbackCalls, 1);
});

test('silent callback is also processed once during effect replay', async () => {
  const auth = await loadAuth();
  await Promise.all([auth.silentCallback(), auth.silentCallback()]);
  assert.equal(globalThis.window.silentCalls, 1);
});

test('a cover network failure does not reject feed preparation', async () => {
  const auth = await loadAuth();
  auth.fetch = async () => { throw new TypeError('NetworkError when attempting to fetch resource.'); };
  assert.equal(await auth.imageUrl('/api/books/example/thumbnail'), undefined);
});

test('an unavailable cover or unreadable response uses the existing fallback', async () => {
  const auth = await loadAuth();
  auth.fetch = async () => new globalThis.Response(null, { status: 404 });
  assert.equal(await auth.imageUrl('/api/books/example/thumbnail'), undefined);
  auth.fetch = async () => ({ ok: true, blob: async () => { throw new TypeError('Body stream failed'); } });
  assert.equal(await auth.imageUrl('/api/books/example/thumbnail'), undefined);
});

test('successful covers still use the authenticated client and a blob URL', async () => {
  const auth = await loadAuth();
  const calls = [];
  auth.fetch = async (...args) => { calls.push(args); return new globalThis.Response(new globalThis.Blob(['image'], { type: 'image/png' })); };
  const image = await auth.imageUrl('/api/books/example/thumbnail');
  assert.match(image, /^blob:/);
  assert.equal(calls[0][0], 'http://localhost:18080/api/books/example/thumbnail');
  URL.revokeObjectURL(image);
  await assert.rejects(auth.imageUrl('https://untrusted.example/cover'), /same-origin/);
  assert.equal(calls.length, 1);
});
