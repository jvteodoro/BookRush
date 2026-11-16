import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { fileURLToPath, URL } from "node:url";
import { Buffer } from "node:buffer";
const { Response } = globalThis;

const bundle = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/bookmarks.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const { createBookmarksClient, parseBookmark } = await import(
  `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString("base64")}`
);
const locator = {
  href: "/versions/v1/chapters/c1/content",
  type: "text/html",
  locations: { progression: 0.4321, totalProgression: 0.31415 },
};
const path = (id) => `/api/v1/reader/books/${id}/bookmarks`;

test("saves the full locator and reads it back across a new client instance", async () => {
  let saved;
  const fetcher = async (url, init) => {
    assert.equal(url, path("book-1"));
    if (init) {
      assert.equal(init.method, "POST");
      assert.equal(init.headers["Content-Type"], "application/json");
      saved = JSON.parse(init.body);
      assert.deepEqual(saved, { locator });
      return Response.json({
        id: "mark-1",
        locator: JSON.stringify(saved.locator),
      });
    }
    return Response.json([
      { id: "mark-1", locator: JSON.stringify(saved.locator) },
    ]);
  };
  const bookmark = await createBookmarksClient(fetcher, path).save(
    "book-1",
    locator,
  );
  const list = await createBookmarksClient(fetcher, path).list("book-1");
  assert.deepEqual(list[0], bookmark);
  assert.deepEqual(list[0].locator, locator);
});

test("HTTP failures reject save and list instead of confirming success", async () => {
  for (const status of [400, 401, 403, 404, 500, 502]) {
    const client = createBookmarksClient(
      async () => new Response(null, { status }),
      path,
    );
    await assert.rejects(
      client.save("book-1", locator),
      new RegExp(`HTTP ${status}`),
    );
    await assert.rejects(client.list("book-1"), new RegExp(`HTTP ${status}`));
  }
});

test("network errors propagate and legacy or corrupt locators are unavailable", async () => {
  const client = createBookmarksClient(async () => {
    throw new Error("offline");
  }, path);
  await assert.rejects(client.save("book-1", locator), /offline/);
  await assert.rejects(client.list("book-1"), /offline/);
  for (const value of [null, undefined, "broken", "42", "{}", "null"]) {
    assert.equal(parseBookmark({ id: "legacy", locator: value }).locator, null);
  }
});
