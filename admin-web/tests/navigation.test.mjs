import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { fileURLToPath, URL } from "node:url";
import { test } from "node:test";
import { build } from "esbuild";

const bundled = await build({
  entryPoints: [fileURLToPath(new URL("../src/app/navigation.ts", import.meta.url))],
  bundle: true,
  format: "esm",
  platform: "browser",
  write: false,
});
const navigation = await import(
  `data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`,
);

test("admin navigation exposes every routed console area", () => {
  assert.deepEqual(
    navigation.adminNavigation.map((item) => item.to),
    ["/", "/catalog", "/review", "/analytics", "/feed-lab", "/training"],
  );
  assert.equal(navigation.isAdminRoute("/review"), true);
  assert.equal(navigation.isAdminRoute("/missing"), false);
});
