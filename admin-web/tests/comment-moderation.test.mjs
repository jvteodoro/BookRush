import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { fileURLToPath, URL } from "node:url";
import { test } from "node:test";
import { build } from "esbuild";

const calls = [];
let response;
let failure;
globalThis.moderationTestHttp = {
  async get(path, options) {
    calls.push({ method: "GET", path, options });
    if (failure) throw failure;
    return { data: response };
  },
  async post(path, body) {
    calls.push({ method: "POST", path, body });
    if (failure) throw failure;
    return { data: response };
  },
};
const bundled = await build({
  entryPoints: [fileURLToPath(new URL("../src/lib/services.ts", import.meta.url))],
  bundle: true,
  format: "esm",
  platform: "browser",
  write: false,
  define: { "import.meta.env": "{}" },
  plugins: [{
    name: "authenticated-http-transport",
    setup(builder) {
      builder.onLoad({ filter: /\/lib\/http\.ts$/ }, () => ({
        contents: "export const createHttp = () => globalThis.moderationTestHttp;",
        loader: "js",
      }));
    },
  }],
});
const { services } = await import(
  `data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`,
);

test("comment list sends server filters and adapts pagination and legacy field names", async () => {
  response = {
    items: [{ id: "comment-1", book_id: "book-1", subject_key: "reader-1", author_name: "Ana",
      body: "Review", visibility: "HIDDEN", reason_code: "spam", updated_by_subject: "moderator-1" }],
    page: 2, size: 25, totalItems: 60, last: true,
  };
  const result = await services.listAdminComments(" Ana ", "HIDDEN", 2);
  assert.deepEqual(calls.at(-1), { method: "GET", path: "/api/v1/admin/moderation/comments/page",
    options: { params: { search: "Ana", visibility: "HIDDEN", page: 2, size: 25 } } });
  assert.equal(result.items[0].authorName, "Ana");
  assert.equal(result.items[0].updatedBy, "moderator-1");
  assert.equal(result.items[0].reasonCode, "spam");
  assert.equal(result.totalItems, 60);
  assert.equal(result.last, true);
});

test("moderation sends the operator explanation and keeps a failed decision as an error", async () => {
  response = { updated: true };
  await services.moderateAdminComment("comment-1", "REMOVED", "conteúdo ofensivo");
  assert.deepEqual(calls.at(-1), { method: "POST", path: "/api/v1/admin/moderation/comments",
    body: { commentId: "comment-1", visibility: "REMOVED", reasonCode: "conteúdo ofensivo" } });
  failure = new Error("forbidden");
  await assert.rejects(services.moderateAdminComment("comment-1", "VISIBLE", "revisão"), /forbidden/);
  failure = undefined;
});

test("history preserves the server transition, actor, reason and date", async () => {
  response = [{ id: "decision-1", actor_subject: "moderator-1", created_at: "2026-10-31T08:00:00Z",
    previous_visibility: "VISIBLE", visibility: "HIDDEN", reason: "spam" }];
  const result = await services.commentHistory("comment/1");
  assert.equal(calls.at(-1).path, "/api/v1/admin/moderation/comments/comment%2F1/history");
  assert.deepEqual(result[0], { id: "decision-1", actor: "moderator-1", createdAt: "2026-10-31T08:00:00Z",
    previousVisibility: "VISIBLE", visibility: "HIDDEN", reason: "spam" });
});
