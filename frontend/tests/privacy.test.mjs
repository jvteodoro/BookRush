import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { test } from "node:test";
import { URL, fileURLToPath } from "node:url";
import { build } from "esbuild";

const result = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/privacy.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  platform: "browser",
  format: "esm",
});
const privacy = await import(
  `data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString("base64")}`
);

const response = (body, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
});

test("privacy normalizes granular defaults and preserves legacy profile visibility", () => {
  assert.deepEqual(privacy.normalizePrivacy({ isPublic: false }), {
    profileVisibility: "PRIVATE",
    activityVisibility: "PUBLIC",
    libraryVisibility: "PRIVATE",
    recentVisibility: "PRIVATE",
    socialGraphVisibility: "PUBLIC",
  });
  assert.equal(
    privacy.normalizePrivacy({ profileVisibility: "FOLLOWERS" })
      .profileVisibility,
    "FOLLOWERS",
  );
});

test("privacy loads all persisted audiences from the authenticated endpoint", async () => {
  const calls = [];
  const profile = {
    profileVisibility: "FOLLOWERS",
    activityVisibility: "PRIVATE",
    libraryVisibility: "PUBLIC",
    recentVisibility: "FOLLOWERS",
    socialGraphVisibility: "PRIVATE",
  };
  const loaded = await privacy.loadPrivacy(async (url, init) => {
    calls.push([url, init]);
    return response(profile);
  });
  assert.deepEqual(loaded.preferences, profile);
  assert.deepEqual(calls, [["/api/v1/profile", undefined]]);
});

test("privacy saves only the five granular preferences as JSON", async () => {
  const calls = [];
  const preferences = {
    ...privacy.DEFAULT_PRIVACY,
    libraryVisibility: "FOLLOWERS",
  };
  await privacy.savePrivacy(preferences, async (url, init) => {
    calls.push([url, init]);
    return response(preferences);
  });
  const [url, init] = calls[0];
  assert.equal(url, "/api/v1/profile");
  assert.equal(init.method, "PUT");
  assert.equal(init.headers["Content-Type"], "application/json");
  assert.deepEqual(JSON.parse(init.body), preferences);
});

test("privacy exposes authentication and authorization failures", async () => {
  await assert.rejects(
    privacy.loadPrivacy(async () => response({}, 401)),
    /sessão expirou/,
  );
  await assert.rejects(
    privacy.savePrivacy(privacy.DEFAULT_PRIVACY, async () => response({}, 403)),
    /não pode alterar/,
  );
});
