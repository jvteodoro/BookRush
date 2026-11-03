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
const originBundle = await build({
  entryPoints: [
    fileURLToPath(
      new URL("../src/reader/readerSessionOrigin.ts", import.meta.url),
    ),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const { createReaderSessionOriginStore } = await import(
  `data:text/javascript;base64,${Buffer.from(originBundle.outputFiles[0].text).toString("base64")}`
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

test("mount interaction keeps feed attribution after old reader unmount and isolates direct next book", () => {
  const origin = createReaderSessionOriginStore();
  const feedHeaders = {
    "X-BookRush-Recommendation-Item": "item-a",
    "X-BookRush-Impression": "impression-a",
  };

  origin.request({ bookId: "book-a", headers: feedHeaders });
  const captured = origin.requestedFor("book-a");
  origin.clear();
  origin.activate("book-a", captured);
  assert.deepEqual(origin.activeFor("book-a"), feedHeaders);

  origin.request(null);
  const directCaptured = origin.requestedFor("book-b");
  origin.clear();
  origin.activate("book-b", directCaptured);
  assert.deepEqual(origin.activeFor("book-b"), {});
  assert.deepEqual(origin.activeFor("book-a"), {});
});

test("two recommendation origins never cross during reader replacement", () => {
  const origin = createReaderSessionOriginStore();
  const headersA = {
    "X-BookRush-Recommendation-Item": "item-a",
    "X-BookRush-Impression": "impression-a",
  };
  const headersB = {
    "X-BookRush-Recommendation-Item": "item-b",
    "X-BookRush-Impression": "impression-b",
  };

  origin.request({ bookId: "book-a", headers: headersA });
  const capturedA = origin.requestedFor("book-a");
  origin.clear();
  origin.activate("book-a", capturedA);
  assert.deepEqual(origin.activeFor("book-a"), headersA);

  origin.request({ bookId: "book-b", headers: headersB });
  const capturedB = origin.requestedFor("book-b");
  origin.clear();
  origin.activate("book-b", capturedB);
  assert.deepEqual(origin.activeFor("book-b"), headersB);
  assert.deepEqual(origin.activeFor("book-a"), {});
});
