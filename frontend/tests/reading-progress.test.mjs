import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { fileURLToPath, URL } from "node:url";
const { Response } = globalThis;

const bundle = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/readingProgress.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const { loadReadingPosition, createReadingProgress } = await import(
  `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString("base64")}`
);
const locator = (n) => ({
  href: "/versions/v1/chapter",
  type: "text/html",
  locations: { progression: n, totalProgression: n },
});

function setup(fetcher) {
  const calls = [];
  const listeners = {};
  const target = {
    visibilityState: "visible",
    addEventListener(name, callback) {
      listeners[name] = callback;
    },
    removeEventListener(name) {
      delete listeners[name];
    },
  };
  let scheduled;
  let warnings = 0;
  const progress = createReadingProgress({
    fetcher: async (path, init) => {
      calls.push({ path, init });
      return fetcher ? fetcher(path, init) : Response.json({});
    },
    path: "/books/book-1/progress",
    headers: { "X-BookRush-Impression": "impression" },
    documentRef: target,
    windowRef: target,
    schedule(callback) {
      scheduled = callback;
      return 1;
    },
    cancel() {
      scheduled = undefined;
    },
    onError() {
      warnings++;
    },
  });
  return {
    progress,
    calls,
    target,
    listeners,
    warnings: () => warnings,
    scheduled: () => scheduled,
  };
}

test("loads a precise locator without cache; legacy records start from the beginning", async () => {
  const saved = locator(0.432123);
  assert.deepEqual(
    await loadReadingPosition(async (path, init) => {
      assert.equal(path, "/progress");
      assert.equal(init.cache, "no-store");
      return Response.json([{ locator: JSON.stringify(saved) }]);
    }, "/progress"),
    saved,
  );
  for (const value of [
    [],
    [{ percent: 43, position_codepoint: 43 }],
    [{ locator: "broken" }],
  ]) {
    assert.equal(
      await loadReadingPosition(async () => Response.json(value), "/progress"),
      null,
    );
  }
});

test("failed restore cannot be mistaken for a new book and overwrite its saved position", async () => {
  for (const status of [401, 403, 502]) {
    await assert.rejects(
      loadReadingPosition(
        async () => new Response(null, { status }),
        "/progress",
      ),
      new RegExp(`HTTP ${status}`),
    );
  }
});

test("rapid movement coalesces to the exact last locator, including zero", async () => {
  const ui = setup();
  ui.progress.record(locator(0.431));
  ui.progress.record(locator(0.432123));
  assert.equal(ui.calls.length, 0);
  await ui.progress.flush();
  const payload = JSON.parse(ui.calls[0].init.body);
  assert.deepEqual(payload.locator, locator(0.432123));
  assert.equal(payload.percent, 43.2123);
  assert.equal(payload.positionCodepoint, 0);
  assert.equal(ui.calls[0].init.headers["X-BookRush-Impression"], "impression");
  ui.progress.record(locator(0));
  await ui.progress.close();
  assert.equal(JSON.parse(ui.calls[1].init.body).percent, 0);
});

test("an older request finishes before the newest position is written", async () => {
  let finish;
  const ui = setup(() =>
    ui.calls.length === 1
      ? new Promise((resolve) => {
          finish = resolve;
        })
      : Response.json({}),
  );
  ui.progress.record(locator(0.2));
  const writing = ui.progress.flush();
  ui.progress.record(locator(0.4));
  ui.progress.record(locator(0.3));
  const closing = ui.progress.close();
  assert.equal(ui.calls.length, 1);
  finish(Response.json({}));
  await writing;
  await closing;
  assert.equal(ui.calls.length, 2);
  assert.deepEqual(JSON.parse(ui.calls[1].init.body).locator, locator(0.3));
  ui.progress.record(locator(0.9));
  await ui.progress.flush();
  assert.equal(ui.calls.length, 2);
  assert.deepEqual(ui.listeners, {});
});

test("hiding or leaving the page flushes with keepalive and no duplicate save", async () => {
  const ui = setup();
  ui.progress.record(locator(0.5));
  ui.target.visibilityState = "hidden";
  ui.listeners.visibilitychange();
  await ui.progress.flush();
  assert.equal(ui.calls[0].init.keepalive, true);
  ui.progress.record(locator(0.6));
  ui.listeners.pagehide();
  await ui.progress.flush();
  await ui.progress.close();
  assert.equal(ui.calls.length, 2);
});

test("save errors retain the latest position for retry and report one warning", async () => {
  let online = false;
  const ui = setup(() => new Response(null, { status: online ? 200 : 502 }));
  ui.progress.record(locator(0.7));
  await ui.progress.flush();
  await ui.progress.flush();
  assert.equal(ui.warnings(), 1);
  online = true;
  await ui.progress.close();
  assert.equal(ui.calls.length, 3);
  assert.deepEqual(JSON.parse(ui.calls[2].init.body).locator, locator(0.7));
});
