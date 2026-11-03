import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { fileURLToPath, URL } from "node:url";

const bundle = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/readerSession.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const { createReaderSessionLifecycle } = await import(
  `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString("base64")}`
);

function harness(responses = [{ id: "session-1" }]) {
  let now = 1_000_000;
  const calls = [];
  const listeners = new Map();
  const documentRef = {
    visibilityState: "visible",
    addEventListener: (name, listener) =>
      listeners.set(`document:${name}`, listener),
    removeEventListener: (name) => listeners.delete(`document:${name}`),
  };
  const windowRef = {
    addEventListener: (name, listener) =>
      listeners.set(`window:${name}`, listener),
    removeEventListener: (name) => listeners.delete(`window:${name}`),
  };
  const lifecycle = createReaderSessionLifecycle({
    sessionsPath: "/reader/sessions",
    endSessionPath: (id) => `/reader/sessions/${id}/end`,
    documentRef,
    windowRef,
    now: () => now,
    fetcher: async (input, init) => {
      calls.push({ input, init: { ...init, headers: { ...init.headers } } });
      const payload = responses.shift() ?? {};
      return { ok: true, json: async () => payload };
    },
  });
  return {
    lifecycle,
    calls,
    listeners,
    documentRef,
    advance: (seconds) => {
      now += seconds * 1000;
    },
  };
}

test("feed-origin reader session starts, measures active time and closes the same session", async () => {
  const testCase = harness();
  await testCase.lifecycle.start("book-1", {
    "X-BookRush-Recommendation-Item": "item-1",
    "X-BookRush-Impression": "impression-1",
  });
  testCase.advance(12);
  await testCase.lifecycle.end();

  assert.equal(testCase.calls.length, 2);
  assert.equal(testCase.calls[0].input, "/reader/sessions");
  assert.deepEqual(JSON.parse(testCase.calls[0].init.body), {
    bookId: "book-1",
  });
  assert.equal(
    testCase.calls[0].init.headers["X-BookRush-Recommendation-Item"],
    "item-1",
  );
  assert.equal(
    testCase.calls[0].init.headers["X-BookRush-Impression"],
    "impression-1",
  );
  assert.equal(testCase.calls[1].input, "/reader/sessions/session-1/end");
  assert.deepEqual(JSON.parse(testCase.calls[1].init.body), {
    activeSeconds: 12,
  });
  assert.equal(testCase.calls[1].init.keepalive, true);
  assert.equal(testCase.listeners.has("document:visibilitychange"), false);
  assert.equal(testCase.listeners.has("window:pagehide"), false);
});

test("direct reader session sends no recommendation attribution and remains plant-independent", async () => {
  const testCase = harness();
  await testCase.lifecycle.start("book-2");
  testCase.advance(4);
  await testCase.lifecycle.end();

  assert.deepEqual(testCase.calls[0].init.headers, {
    "Content-Type": "application/json",
  });
  assert.deepEqual(JSON.parse(testCase.calls[1].init.body), {
    activeSeconds: 4,
  });
});
