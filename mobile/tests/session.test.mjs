import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import ts from "typescript";

const source = await readFile(
  new URL("../src/updateSavedBook.ts", import.meta.url),
  "utf8",
);
const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
});
const { updateSavedBook } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`
);

test("mobile restores the prior saved value when the server rejects an optimistic save", async () => {
  const values = [];
  await assert.rejects(
    updateSavedBook(
      true,
      (value) => values.push(value),
      async () => {
        throw new Error("403");
      },
    ),
  );
  assert.deepEqual(values, [false, true]);
  await updateSavedBook(
    false,
    (value) => values.push(value),
    async () => {},
  );
  assert.deepEqual(values, [false, true, true]);
});
