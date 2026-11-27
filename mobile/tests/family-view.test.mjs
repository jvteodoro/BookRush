import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { after, test } from "node:test";
import ts from "typescript";

// The shared family module is TypeScript outside this package, so transpile it into a
// temporary directory and import it: the same rules drive the mobile family screen.
const sharedDir = new URL("../../packages/session/", import.meta.url);
const outDir = await mkdtemp(join(tmpdir(), "family-view-"));
after(() => rm(outDir, { recursive: true, force: true }));
for (const name of ["session", "family"]) {
  const source = await readFile(new URL(`${name}.ts`, sharedDir), "utf8");
  const output = ts
    .transpileModule(source, {
      compilerOptions: {
        module: ts.ModuleKind.ESNext,
        target: ts.ScriptTarget.ES2020,
      },
    })
    .outputText.replace(/from "\.\/session"/g, 'from "./session.mjs"');
  await writeFile(join(outDir, `${name}.mjs`), output);
}
const family = await import(pathToFileURL(join(outDir, "family.mjs")).href);

test("mobile family states come from the shared familyView rules", () => {
  const premium = { premiumFamily: true };
  const free = { premiumFamily: false };
  const owner = {
    id: "h",
    role: "OWNER",
    additionalSeats: 5,
    occupiedOrReserved: 1,
    members: [],
  };
  const member = {
    id: "h",
    role: "MEMBER",
    additionalSeats: 5,
    occupiedOrReserved: 2,
    members: [],
  };
  assert.equal(family.familyView(undefined, undefined), "loading");
  assert.equal(family.familyView(free, null), "guest");
  assert.equal(family.familyView(premium, null), "premium-without-family");
  assert.equal(family.familyView(premium, owner), "owner");
  assert.equal(family.familyView(free, member), "member");
});

test("the mobile screen renders its state through familyView", async () => {
  const screen = await readFile(
    new URL("../src/FamilyScreen.tsx", import.meta.url),
    "utf8",
  );
  assert.match(screen, /familyView\(subscription, household\)/);
  assert.match(screen, /view === "premium-without-family"/);
});
