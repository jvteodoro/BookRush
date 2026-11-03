import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { fileURLToPath, URL } from "node:url";
import { setImmediate } from "node:timers";

const bundle = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/readingTimer.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
  plugins: [
    {
      name: "fake-auth",
      setup(builder) {
        builder.onResolve({ filter: /^\.\.\/auth$/ }, () => ({
          path: "auth",
          namespace: "fake",
        }));
        builder.onLoad({ filter: /.*/, namespace: "fake" }, () => ({
          contents:
            "export const authClient = { fetch: globalThis.fakePlantFetch };",
          loader: "js",
        }));
      },
    },
  ],
});

test("counts visible reading and flushes once when closed", async () => {
  let now = 1000000;
  const originalNow = Date.now;
  const documentListeners = new Map();
  const windowListeners = new Map();
  let interval;
  const sent = [];
  Date.now = () => now;
  globalThis.document = {
    visibilityState: "visible",
    addEventListener: (name, fn) => documentListeners.set(name, fn),
    removeEventListener: (name) => documentListeners.delete(name),
  };
  globalThis.window = {
    setInterval: (fn) => {
      interval = fn;
      return 1;
    },
    clearInterval: () => {},
    addEventListener: (name, fn) => windowListeners.set(name, fn),
    removeEventListener: (name) => windowListeners.delete(name),
    dispatchEvent: () => {},
  };
  globalThis.fakePlantFetch = async (_url, init) => {
    sent.push(JSON.parse(init.body));
    if (sent.length === 1) return { ok: false };
    return { ok: true, json: async () => ({ stage: 1 }) };
  };
  try {
    const { startReadingTimer } = await import(
      `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString("base64")}`
    );
    const stop = startReadingTimer();
    now += 15000;
    interval();
    now += 5000;
    globalThis.document.visibilityState = "hidden";
    documentListeners.get("visibilitychange")();
    now += 60000;
    globalThis.document.visibilityState = "visible";
    documentListeners.get("visibilitychange")();
    now += 3000;
    stop();
    stop();
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(
      sent.map((item) => item.activeSeconds),
      [15, 15, 5, 3],
    );
    assert.equal(sent[0].eventId, sent[1].eventId);
    assert.equal(new Set(sent.map((item) => item.eventId)).size, 3);
  } finally {
    Date.now = originalNow;
    delete globalThis.document;
    delete globalThis.window;
    delete globalThis.fakePlantFetch;
  }
});
