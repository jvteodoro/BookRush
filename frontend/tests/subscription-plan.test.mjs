import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { test } from "node:test";
import { URL, fileURLToPath } from "node:url";
import { build } from "esbuild";

const result = await build({
  entryPoints: [
    fileURLToPath(
      new URL("../../packages/session/subscription.ts", import.meta.url),
    ),
  ],
  bundle: true,
  write: false,
  platform: "browser",
  format: "esm",
});
const subscription = await import(
  `data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString("base64")}`
);

const reply = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
});

function recorder(responses) {
  const calls = [];
  const request = async (path, init = {}) => {
    calls.push({
      path,
      method: init.method ?? "GET",
      headers: init.headers,
      body: init.body,
    });
    return responses.shift();
  };
  return { request, calls };
}

const active = {
  plan: "PREMIUM_FAMILY",
  planVersion: 1,
  state: "ACTIVE",
  effectiveUntil: "2026-12-27T07:47:44Z",
  version: 1,
  publishBooks: true,
  premiumFamily: true,
  additionalSeats: 5,
};

test("checkout sends the Premium Família plan with an idempotency key", async () => {
  const { request, calls } = recorder([
    reply(201, { simulation: true, realPayment: false, state: "ACTIVE" }),
  ]);
  const key = "7d4f4d3e-1111-4222-8333-944444444444";
  await subscription.checkoutPremiumFamily(request, key);
  assert.equal(calls[0].path, "/api/v1/subscription/checkout");
  assert.equal(calls[0].method, "POST");
  assert.equal(calls[0].headers["Idempotency-Key"], key);
  assert.deepEqual(JSON.parse(calls[0].body), { planId: "premium-family" });
});

test("checkout rejects a failed provider response with a user message", async () => {
  const { request } = recorder([reply(409, {})]);
  await assert.rejects(
    () =>
      subscription.checkoutPremiumFamily(
        request,
        "7d4f4d3e-1111-4222-8333-944444444444",
      ),
    (cause) => cause.message === subscription.subscriptionMessage(409),
  );
});

test("cancel issues POST on the cancel endpoint and accepts 202", async () => {
  const { request, calls } = recorder([reply(202, { version: 2 })]);
  await subscription.cancelSubscription(request);
  assert.equal(calls[0].path, "/api/v1/subscription/cancel");
  assert.equal(calls[0].method, "POST");
});

test("state labels describe the subscription without exposing internal states", () => {
  assert.equal(subscription.subscriptionStateLabel(active), "Assinatura ativa");
  assert.equal(
    subscription.subscriptionStateLabel({ ...active, state: "CANCEL_AT_END" }),
    "Cancelamento agendado",
  );
  assert.equal(
    subscription.subscriptionStateLabel({
      ...active,
      state: "FREE",
      premiumFamily: false,
    }),
    "Sem assinatura Premium Família",
  );
});

test("the plan keeps the family benefits list non-empty", () => {
  assert.ok(subscription.premiumFamilyBenefits.length >= 3);
});
