import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { test } from "node:test";
import { URL, fileURLToPath } from "node:url";
import { build } from "esbuild";

const bundled = await build({
  entryPoints: [fileURLToPath(new URL("../src/telemetry.ts", import.meta.url))],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
globalThis.window = {
  setTimeout: globalThis.setTimeout,
  clearTimeout: globalThis.clearTimeout,
};
globalThis.IntersectionObserver = class {
  constructor(callback) {
    this.callback = callback;
  }
  observe() {
    this.callback([{ isIntersecting: true, intersectionRatio: 0.75 }]);
  }
  disconnect() {}
};
const { createTelemetry } = await import(
  `data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`
);

test("telemetry batches events and sanitizes payloads", async () => {
  const batches = [];
  const telemetry = createTelemetry(
    async (batch) => batches.push(batch),
    60_000,
  );
  telemetry.track("BOOK_OPEN", "book-1", {
    ok: "yes",
    undefinedValue: undefined,
    oversized: "x".repeat(300),
  });
  assert.equal(telemetry.pending(), 1);
  await telemetry.flush();
  assert.equal(batches.length, 1);
  assert.equal(batches[0][0].payload.ok, "yes");
  assert.equal(batches[0][0].payload.undefinedValue, undefined);
  assert.equal(batches[0][0].payload.oversized, undefined);
});

test("telemetry retries a failed batch without losing events", async () => {
  let attempts = 0;
  const telemetry = createTelemetry(async (batch) => {
    attempts++;
    if (attempts === 1) throw new Error("temporary");
    assert.equal(batch.length, 1);
  }, 60_000);
  telemetry.track("BOOK_OPEN", "book-2");
  await telemetry.flush();
  assert.equal(telemetry.pending(), 1);
  await telemetry.flush();
  assert.equal(attempts, 2);
  assert.equal(telemetry.pending(), 0);
});

test("telemetry records impression and non-negative dwell duration", async () => {
  const events = [];
  const telemetry = createTelemetry(
    async (batch) => events.push(...batch),
    60_000,
  );
  telemetry.observeImpression({}, "book-3", 0.5);
  telemetry.dwell("book-3", Date.now() + 1000);
  await telemetry.flush();
  assert.deepEqual(
    events.map((event) => event.eventType),
    ["EXCERPT_IMPRESSION", "DWELL"],
  );
  assert.equal(events[1].payload.durationMs, 0);
});
