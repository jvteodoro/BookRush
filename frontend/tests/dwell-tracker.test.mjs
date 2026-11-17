import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { test } from "node:test";
import { fileURLToPath, URL } from "node:url";
import { build } from "esbuild";

const bundled = await build({
  entryPoints: [
    fileURLToPath(
      new URL("../src/components/book/dwellTracker.ts", import.meta.url),
    ),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const { createDwellTracker } = await import(
  `data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`
);

test("dwell tracker emits one interval after a visible card leaves", () => {
  let now = 100;
  const events = [];
  const tracker = createDwellTracker(
    (bookId, durationMs) => events.push({ bookId, durationMs }),
    () => now,
  );

  tracker.update("book-1", true);
  now = 640;
  tracker.update("book-1", false);
  tracker.update("book-1", false);

  assert.deepEqual(events, [{ bookId: "book-1", durationMs: 540 }]);
});

test("dwell tracker ignores short intervals and flushes active cards", () => {
  let now = 0;
  const events = [];
  const tracker = createDwellTracker(
    (bookId, durationMs) => events.push({ bookId, durationMs }),
    () => now,
  );

  tracker.update("short", true);
  now = 100;
  tracker.update("short", false);
  tracker.update("long", true);
  now = 400;
  tracker.flush();

  assert.deepEqual(events, [{ bookId: "long", durationMs: 300 }]);
});

test("dwell tracker pauses intervals while the page is hidden", () => {
  let now = 0;
  const events = [];
  const tracker = createDwellTracker(
    (bookId, durationMs) => events.push({ bookId, durationMs }),
    () => now,
  );

  tracker.update("book-1", true);
  now = 400;
  tracker.setPageVisible(false);
  now = 900;
  tracker.setPageVisible(true);
  now = 1200;
  tracker.flush();

  assert.deepEqual(events, [
    { bookId: "book-1", durationMs: 400 },
    { bookId: "book-1", durationMs: 300 },
  ]);
});
