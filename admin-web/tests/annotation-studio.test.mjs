import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath, URL } from "node:url";
import { test } from "node:test";

const source = await readFile(
  fileURLToPath(
    new URL("../src/features/training/TrainingPage.tsx", import.meta.url),
  ),
  "utf8",
);

test("Annotation Studio keeps blind mode and keyboard contract", () => {
  assert.match(source, /data-rating-code=\{code\}/);
  assert.match(source, /\^\[1-5\]\$\/\.test/);
  assert.match(source, /aria-live="polite"/);
  assert.match(source, /bookrush\.annotation-studio\.pending/);
  assert.match(source, /annotationProgress/);
  assert.doesNotMatch(source, /candidate_score|modelFeatures.*item/);
});

test("Annotation Studio advances after diagnostic submission", () => {
  assert.match(source, /submitDiagnostic/);
  assert.match(source, /await nextExcerpt\(\)/);
  assert.match(source, /Submit diagnostic e próximo excerpt/);
});
