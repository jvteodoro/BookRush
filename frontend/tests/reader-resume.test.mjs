import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { fileURLToPath, URL } from "node:url";
import { setTimeout } from "node:timers";
const { Response } = globalThis;

const shared = `
  export class Locator {
    constructor(value) { Object.assign(this, value); this.value = value; }
    static deserialize(value) { return value?.href ? new Locator(value) : undefined; }
    serialize() { return this.value; }
  }
  export class Manifest { static deserialize(value) { return { readingOrder: { items: value.readingOrder } }; } }
  export class Publication { constructor({manifest}) { this.manifest = manifest; this.readingOrder = manifest.readingOrder; } }
  export class HttpFetcher {}
`;
const stubs = {
  "@readium/shared": shared,
  "@readium/navigator": `
    import { Locator } from '@readium/shared';
    export class WebPubPreferences {}
    export class WebPubNavigator {
      constructor(container, publication, listeners, initial) {
        this.listeners = listeners; this.initial = initial;
        this.beginning = Locator.deserialize({ href: publication.readingOrder.items[0].href, type: 'text/html', locations: {progression: 0} });
        this.currentLocator = initial || this.beginning;
        globalThis.__resume.navigators.push(this);
      }
      async load() { this.listeners.positionChanged(this.beginning); this.listeners.positionChanged(this.currentLocator); }
      async destroy() { this.destroyed = true; }
      async submitPreferences() {}
      go(locator, animated, callback) { this.currentLocator = locator; this.listeners.positionChanged(locator); callback(true); }
    }
  `,
  "../auth": `export const authClient = {fetch: (...args) => globalThis.__resume.fetch(...args)};`,
  "./readingTimer": `export const startReadingTimer = () => () => {};`,
  "./readerSession": `export const createReaderSessionLifecycle = () => ({start: async () => {}, end: async () => {}});`,
  "./readerSessionOrigin": `export const readerSessionOrigin = {requestedFor: () => ({}), activate() {}, clear() {}};`,
};
const bundle = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/readiumBridge.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
  plugins: [
    {
      name: "reader-dependencies",
      setup(build) {
        build.onResolve({ filter: /.*/ }, (args) =>
          args.path in stubs
            ? { path: args.path, namespace: "stub" }
            : undefined,
        );
        build.onLoad({ filter: /.*/, namespace: "stub" }, (args) => ({
          contents: stubs[args.path],
          loader: "js",
        }));
      },
    },
  ],
});
const { readiumBridge } = await import(
  `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString("base64")}`
);
const tick = () => new Promise((resolve) => setTimeout(resolve, 0));
const locator = {
  href: "/versions/v1/chapter",
  type: "text/html",
  locations: { progression: 0.432123, totalProgression: 0.314159 },
};

async function setup(saved = locator) {
  await readiumBridge.unmount();
  const listeners = {};
  const target = {
    visibilityState: "visible",
    location: { origin: "https://reader.example" },
    addEventListener(name, handler) {
      listeners[name] = handler;
    },
    removeEventListener(name) {
      delete listeners[name];
    },
  };
  globalThis.window = target;
  globalThis.document = target;
  const harness = {
    navigators: [],
    writes: [],
    saved,
    fetch: async (path, init) => {
      if (path.endsWith("publication.json"))
        return Response.json({ readingOrder: [{ href: locator.href }] });
      if (init?.method === "PUT") {
        const value = JSON.parse(init.body);
        harness.saved = value.locator;
        harness.writes.push(value);
        return Response.json({});
      }
      return Response.json(
        harness.saved ? [{ locator: JSON.stringify(harness.saved) }] : [],
      );
    },
  };
  globalThis.__resume = harness;
  return {
    harness,
    container: { style: { setProperty() {} }, querySelector: () => null },
  };
}

test("reopening initializes Readium at the exact last position without writing its temporary first page", async () => {
  const { harness, container } = await setup();
  await readiumBridge.mount("book-1", container);
  assert.deepEqual(harness.navigators[0].initial.serialize(), locator);
  assert.equal(harness.writes.length, 0);
  const next = {
    ...locator,
    locations: { progression: 0.531234, totalProgression: 0.413579 },
  };
  const nav = harness.navigators[0];
  nav.currentLocator = { locations: next.locations, serialize: () => next };
  nav.listeners.positionChanged(nav.currentLocator);
  await readiumBridge.unmount();
  assert.deepEqual(
    harness.writes.map((value) => value.locator),
    [next],
  );
  await readiumBridge.mount("book-1", container);
  assert.deepEqual(harness.navigators[1].initial.serialize(), next);
  await readiumBridge.unmount();
});

test("a different content version starts at the beginning with a notice", async () => {
  const { harness, container } = await setup({
    ...locator,
    href: "/versions/old/chapter",
  });
  const notices = [];
  await readiumBridge.mount("book-1", container, undefined, (value) =>
    notices.push(value),
  );
  assert.equal(harness.navigators[0].initial, undefined);
  assert.match(notices[0], /outra versão/);
  await readiumBridge.unmount();
});

test("closing during restore cannot create a navigator or overwrite the saved position", async () => {
  const { harness, container } = await setup();
  const fetch = harness.fetch;
  let finish;
  harness.fetch = (path, init) =>
    path.endsWith("/progress") && !init?.method
      ? new Promise((resolve) => {
          finish = resolve;
        })
      : fetch(path, init);
  const opening = readiumBridge.mount("book-1", container);
  await tick();
  const closing = readiumBridge.unmount();
  finish(Response.json([{ locator: JSON.stringify(locator) }]));
  await opening;
  await closing;
  assert.equal(harness.navigators.length, 0);
  assert.equal(harness.writes.length, 0);
});
