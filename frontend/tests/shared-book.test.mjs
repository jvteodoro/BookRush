import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { fileURLToPath, URL } from "node:url";

const output = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/sharedBook.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const { sharedBookPath, loadSharedBook } = await import(
  `data:text/javascript;base64,${Buffer.from(output.outputFiles[0].text).toString("base64")}`
);
const id = "12345678-1234-1234-1234-123456789abc";
test("recognizes book paths without consuming profile routes", () => {
  assert.equal(sharedBookPath(`/book/${id}`), id);
  assert.equal(sharedBookPath(`/book/${id}/`), id);
  assert.equal(sharedBookPath("/book/%zz"), "");
  assert.equal(sharedBookPath("/book/"), "");
  assert.equal(sharedBookPath("/usuarios/alice"), null);
});
test("fetches actual metadata independent of the feed", async () => {
  const controller = new globalThis.AbortController();
  const book = await loadSharedBook(
    id,
    controller.signal,
    async (url, options) => {
      assert.equal(url, `/api/v1/books/${id}`);
      assert.equal(options.signal, controller.signal);
      return new globalThis.Response(
        JSON.stringify({
          id,
          canonicalTitle: "Meu livro",
          authors: ["Autora"],
          description: "Sinopse",
        }),
      );
    },
  );
  assert.equal(book.title, "Meu livro");
  assert.equal(book.author, "Autora");
});
test("invalid IDs and HTTP failures produce explicit errors", async () => {
  let calls = 0;
  await assert.rejects(
    loadSharedBook("bad", undefined, async () => {
      calls++;
    }),
    /inválido/,
  );
  assert.equal(calls, 0);
  for (const [status, message] of [
    [400, /inválido/],
    [401, /sessão/],
    [403, /permissão/],
    [404, /não encontrado/],
    [500, /carregar/],
  ]) {
    await assert.rejects(
      loadSharedBook(
        id,
        undefined,
        async () => new globalThis.Response(null, { status }),
      ),
      message,
    );
  }
});
test("cancelled and failed requests are not substituted with a different book", async () => {
  const controller = new globalThis.AbortController();
  controller.abort();
  await assert.rejects(
    loadSharedBook(id, controller.signal, async (_, options) => {
      assert.equal(options.signal.aborted, true);
      throw new globalThis.DOMException("aborted", "AbortError");
    }),
    /aborted/,
  );
  await assert.rejects(
    loadSharedBook(
      id,
      undefined,
      async () =>
        new globalThis.Response(
          JSON.stringify({ id: "different", canonicalTitle: "Outro" }),
        ),
    ),
    /indisponível/,
  );
});

test("an inactive catalog book is unavailable", async () => {
  await assert.rejects(
    loadSharedBook(
      id,
      undefined,
      async () =>
        new globalThis.Response(
          JSON.stringify({
            id,
            canonicalTitle: "Indisponível",
            status: "INACTIVE",
          }),
        ),
    ),
    /indisponível/,
  );
});
