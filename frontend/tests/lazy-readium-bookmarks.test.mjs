import assert from 'node:assert/strict';
import { test } from 'node:test';
import { build } from 'esbuild';

// Exercise the lazy boundary used by the app, without loading Readium or a DOM.
test('lazy reader forwards bookmark navigation, locator and notices', async () => {
  const bundle = await build({
    entryPoints: ['src/reader/lazyReadiumBridge.ts'], bundle: true,
    write: false, format: 'esm', platform: 'browser',
    plugins: [{ name: 'reader-stub', setup(build) {
      build.onResolve({filter: /^\.\/readiumBridge$/}, () => ({path:'reader', namespace:'stub'}));
      build.onLoad({filter: /.*/, namespace:'stub'}, () => ({contents: `
        let locator = null;
        export const readiumBridge = {
          currentLocator: () => locator,
          goToLocator: async value => { locator = value; return true; },
          mount: async (book, container, changed, notice) => {
            changed?.(0.5); notice?.('restored');
          }
        };
      `}));
    }}],
  });
  const {readiumBridge} = await import('data:text/javascript;base64,' + Buffer.from(bundle.outputFiles[0].text).toString('base64'));
  assert.equal(readiumBridge.currentLocator(), null);
  let progress, notice;
  await readiumBridge.mount('book', {}, value => {progress = value;}, value => {notice = value;});
  assert.equal(progress, 0.5);
  assert.equal(notice, 'restored');
  const locator = {href:'chapter.xhtml', locations:{progression:0.5}};
  assert.equal(await readiumBridge.goToLocator(locator), true);
  assert.deepEqual(readiumBridge.currentLocator(), locator);
});
