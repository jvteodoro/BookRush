import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { URL } from "node:url";
import { Buffer } from "node:buffer";
import { setImmediate } from "node:timers";
/* global Response */
import ts from "typescript";

async function typed(url) {
  const source = await readFile(new URL(url, import.meta.url), "utf8");
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  });
  return import(
    `data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`
  );
}
const { createSessionGate, allowsProduct, invitationAction } = await typed(
  "../../packages/session/session.ts",
);

const next = () => new Promise((resolve) => setImmediate(resolve));
const snapshot = (state, capabilities, reasonCode) =>
  Response.json({
    username: "synthetic",
    accessState: state,
    capabilities,
    reasonCode,
    validUntil: new Date(Date.now() + 60_000).toISOString(),
  });

test("both clients require server capability, and approval and revocation work with one credential", async () => {
  const snapshots = [
    snapshot(
      "MINOR_AWAITING_AUTHORIZATION",
      ["SESSION_VIEW", "INVITE_SELF"],
      "SUPERVISION_REQUIRED",
    ),
    snapshot("MINOR_AUTHORIZED", ["SESSION_VIEW", "PRODUCT_USE"]),
    snapshot(
      "MINOR_FAMILY_INACTIVE",
      ["SESSION_VIEW", "INVITE_SELF"],
      "ACCOUNT_RESTRICTED",
    ),
  ];
  const seen = [];
  const calls = [];
  const gate = createSessionGate(
    async (path) => {
      calls.push(path);
      return snapshots.shift();
    },
    (view) => seen.push(view),
  );
  try {
    assert.equal(gate.allowsProduct(), false);
    gate.refresh();
    await next();
    assert.equal(gate.allowsProduct(), false);
    assert.ok(seen.at(-1).session.capabilities.includes("INVITE_SELF"));
    gate.refresh(true);
    await next();
    assert.equal(gate.allowsProduct(), true);
    gate.refresh(true);
    assert.equal(gate.allowsProduct(), false);
    await next();
    assert.equal(gate.allowsProduct(), false);
    assert.deepEqual(calls, Array(3).fill("/api/v1/session"));
    assert.equal(
      invitationAction("foo/bar", "accept"),
      "/api/v1/family/invitations/foo%2Fbar/accept",
    );
  } finally {
    gate.dispose();
  }
});

test("an expired or technically unavailable snapshot never opens the product", async () => {
  const responses = [
    snapshot("ADULT_ELIGIBLE", ["SESSION_VIEW", "PRODUCT_USE"]),
    new Response(null, { status: 503 }),
    new Response(null, { status: 401 }),
  ];
  let view;
  const gate = createSessionGate(
    async () => responses.shift(),
    (result) => {
      view = result;
    },
  );
  try {
    gate.refresh();
    await next();
    assert.equal(allowsProduct(view), true);
    assert.equal(allowsProduct(view, Date.now() + 61_000), false);
    gate.refresh();
    await next();
    assert.deepEqual(view, { status: "error", authenticate: false });
    assert.equal(gate.allowsProduct(), false);
    gate.refresh();
    await next();
    assert.deepEqual(view, { status: "error", authenticate: true });
  } finally {
    gate.dispose();
  }
});

test("a late response after logout or switch cannot publish an old account's data", async () => {
  let finish;
  let signal;
  const seen = [];
  const gate = createSessionGate(
    (_path, options) => {
      signal = options.signal;
      return new Promise((resolve) => {
        finish = resolve;
      });
    },
    (view) => seen.push(view),
  );
  gate.refresh();
  gate.dispose();
  assert.equal(signal.aborted, true);
  finish(snapshot("ADULT_ELIGIBLE", ["SESSION_VIEW", "PRODUCT_USE"]));
  await next();
  assert.deepEqual(seen, []);
});
