import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { URL, fileURLToPath } from "node:url";
import { test } from "node:test";
import { build } from "esbuild";
const bundled = await build({
  entryPoints: [
    fileURLToPath(
      new URL("../src/features/publisher/publisherMetrics.ts", import.meta.url),
    ),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
  define: { "import.meta.env": "{}" },
});
const { metricTotals, publisherApi } = await import(
  `data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`
);
const row = {
  day: "2026-11-10",
  impressions: 12,
  opens: 4,
  reads: 3,
  likes: 2,
  shares: 1,
  comments: 2,
};
test("publication totals sum each metric without counting days or users as reads", () => {
  assert.deepEqual(metricTotals([row, { ...row, day: "2026-11-09" }]), {
    impressions: 24,
    opens: 8,
    reads: 6,
    likes: 4,
    shares: 2,
    comments: 4,
  });
  assert.deepEqual(metricTotals([]), {
    impressions: 0,
    opens: 0,
    reads: 0,
    likes: 0,
    shares: 0,
    comments: 0,
  });
});
test("invalid aggregates cannot silently become a zero activity report", () => {
  assert.throws(() => metricTotals([{ ...row, reads: -1 }]));
  assert.throws(() => metricTotals([{ ...row, reads: undefined }]));
});
test("metrics client is available independently from mocked analytics", () => {
  assert.equal(typeof publisherApi.submissions, "function");
  assert.equal(typeof publisherApi.metrics, "function");
});
