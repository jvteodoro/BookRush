import assert from "node:assert/strict";
import { createElement } from "react";
import { act, create } from "react-test-renderer";
import { register } from "node:module";
import { test } from "node:test";

register("./component/loader.mjs", import.meta.url);
const { MobileFamily } = await import("../src/FamilyScreen.tsx");

const free = {
  plan: "FREE",
  planVersion: 1,
  state: "FREE",
  effectiveUntil: null,
  version: 0,
  publishBooks: false,
  premiumFamily: false,
  additionalSeats: 0,
};
const premium = {
  plan: "PREMIUM",
  planVersion: 1,
  state: "ACTIVE",
  effectiveUntil: "2026-12-27T07:47:44Z",
  version: 1,
  publishBooks: true,
  premiumFamily: true,
  additionalSeats: 5,
};
const holderHousehold = {
  id: "049f2dee-e09f-4c15-8137-b803c95e700e",
  role: "OWNER",
  additionalSeats: 5,
  occupiedOrReserved: 1,
  members: [
    {
      id: "d8a8fe71-5c30-4a67-9a74-962931ae56b7",
      state: "ACTIVE",
      delegationEnabled: false,
      version: 2,
      permissionVersion: 0,
    },
  ],
};
const memberHousehold = {
  id: "049f2dee-e09f-4c15-8137-b803c95e700e",
  role: "MEMBER",
  additionalSeats: 5,
  occupiedOrReserved: 2,
  members: [],
};

// Fake API: routes are keyed by "METHOD /path" (query string ignored). Missing routes answer 404.
function fakeApi(routes) {
  const calls = [];
  const request = async (path, init = {}) => {
    const method = init.method ?? "GET";
    const key = `${method} ${path.split("?")[0]}`;
    calls.push(key);
    const route = routes[key];
    if (!route) return { ok: false, status: 404, json: async () => ({}) };
    const status = route.status ?? 200;
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => route.body,
    };
  };
  return { request, calls };
}

// Concatenate every rendered string so phrases split across nodes can be matched.
const allText = (node) =>
  node == null
    ? ""
    : typeof node === "string"
      ? node
      : (node.children ?? []).map(allText).join("");

const textOf = (node) =>
  typeof node === "string" ? node : (node.children ?? []).map(textOf).join("");

async function mount(routes) {
  const api = fakeApi(routes);
  let renderer;
  await act(async () => {
    renderer = create(
      createElement(MobileFamily, { request: api.request, onClose: () => {} }),
    );
  });
  await act(async () => {});
  return { renderer, api, texts: () => allText(renderer.toJSON()) };
}

test("loading state shows the loading message before the data arrives", () => {
  let renderer;
  act(() => {
    renderer = create(
      createElement(MobileFamily, {
        request: () => new Promise(() => {}),
        onClose: () => {},
      }),
    );
  });
  assert.match(JSON.stringify(renderer.toJSON()), /Carregando família/);
  renderer.unmount();
});

test("guest without family sees the code, the inbox and the plan offer", async () => {
  const { texts } = await mount({
    "GET /api/v1/subscription/me": { body: free },
    "GET /api/v1/family/me": { status: 404, body: {} },
    "GET /api/v1/family/invitations/me": { body: [] },
  });
  const html = texts();
  assert.match(html, /Entrar em uma família/);
  assert.match(html, /Gerar código/);
  assert.match(html, /Nenhum convite recebido/);
  assert.match(html, /Assinar Premium Família/);
  assert.doesNotMatch(html, /Convidar alguém/);
});

test("guest accepts a pending invitation through the accept endpoint", async () => {
  const invitation = {
    id: "inv-1",
    state: "PENDING",
    expiresAt: "2026-12-04T09:30:12Z",
  };
  const { renderer, api } = await mount({
    "GET /api/v1/subscription/me": { body: free },
    "GET /api/v1/family/me": { status: 404, body: {} },
    "GET /api/v1/family/invitations/me": { body: [invitation] },
    "POST /api/v1/family/invitations/inv-1/accept": {
      body: { membershipId: "m", householdId: "h" },
    },
  });
  const acceptText = renderer.root.findAll(
    (n) => n.type === "Text" && textOf(n) === "Aceitar",
  )[0];
  let pressable = acceptText;
  while (pressable && pressable.type !== "Pressable")
    pressable = pressable.parent;
  await act(async () => {
    pressable.props.onPress();
  });
  await act(async () => {});
  assert.ok(api.calls.includes("POST /api/v1/family/invitations/inv-1/accept"));
});

test("member sees the membership state and no holder controls", async () => {
  const { texts } = await mount({
    "GET /api/v1/subscription/me": { body: free },
    "GET /api/v1/family/me": { body: memberHousehold },
  });
  const html = texts();
  assert.match(html, /Você é membro de uma família Premium/);
  assert.match(html, /2 de 5 vagas ocupadas ou reservadas/);
  assert.doesNotMatch(html, /Convidar alguém/);
  assert.doesNotMatch(html, /Remover/);
});

test("holder sees members, the invite form and the seats", async () => {
  const { texts } = await mount({
    "GET /api/v1/subscription/me": { body: premium },
    "GET /api/v1/family/me": { body: holderHousehold },
    "GET /api/v1/family/049f2dee-e09f-4c15-8137-b803c95e700e/invitations": {
      body: [],
    },
  });
  const html = texts();
  assert.match(html, /1 de 5 vagas ocupadas ou reservadas/);
  assert.match(html, /Membro · Ativo/);
  assert.match(html, /Convidar alguém/);
  assert.match(html, /Cancelar assinatura/);
});

test("premium holder without family is offered to create one", async () => {
  const { texts } = await mount({
    "GET /api/v1/subscription/me": { body: premium },
    "GET /api/v1/family/me": { status: 404, body: {} },
  });
  const html = texts();
  assert.match(html, /Crie sua família/);
  assert.match(html, /Criar família/);
  assert.doesNotMatch(html, /Assinar Premium Família/);
});
