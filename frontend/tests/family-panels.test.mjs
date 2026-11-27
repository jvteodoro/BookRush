import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { test } from "node:test";
import { URL, fileURLToPath } from "node:url";
import { build } from "esbuild";

// Render the real presentational family component (no hooks, no auth) for each state.
const result = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/family/FamilyPanels.tsx", import.meta.url)),
  ],
  bundle: true,
  write: false,
  platform: "browser",
  format: "esm",
  jsx: "automatic",
  define: { "process.env.NODE_ENV": '"test"' },
});
const { FamilyPanels } = await import(
  `data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString("base64")}`
);

const noop = () => {};
const actions = {
  generateCode: noop,
  copyCode: noop,
  accept: noop,
  decline: noop,
  checkout: noop,
  createFamily: noop,
  removeMember: noop,
  invite: noop,
  setRecipientCode: noop,
  cancelInvitation: noop,
  cancelSubscription: noop,
};
const base = {
  invitations: [],
  received: [],
  recipientCode: "",
  busy: false,
  error: "",
  notice: "",
  actions,
};
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
const active = {
  plan: "PREMIUM",
  planVersion: 1,
  state: "ACTIVE",
  effectiveUntil: "2026-12-27T07:47:44Z",
  version: 1,
  publishBooks: true,
  premiumFamily: true,
  additionalSeats: 5,
};
const owner = {
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
const member = {
  id: "049f2dee-e09f-4c15-8137-b803c95e700e",
  role: "MEMBER",
  additionalSeats: 5,
  occupiedOrReserved: 2,
  members: [],
};
const render = (props) =>
  renderToStaticMarkup(createElement(FamilyPanels, { ...base, ...props }));

test("loading state shows only the loading status", () => {
  const html = render({});
  assert.match(html, /Carregando família/);
  assert.doesNotMatch(html, /Gerar código/);
});

test("guest without family sees the code, the inbox and the plan offer", () => {
  const html = render({ subscription: free, household: null });
  assert.match(html, /Gerar código/);
  assert.match(html, /Nenhum convite recebido/);
  assert.match(html, /Assinar Premium Família/);
  assert.doesNotMatch(html, /Convidar alguém/);
  assert.doesNotMatch(html, /Remover/);
});

test("guest shows the generated code and copy action", () => {
  const html = render({
    subscription: free,
    household: null,
    address: {
      code: "c476d684-64e4-4392-a375-ade5bd64e251",
      expiresAt: "2026-12-04T09:30:12Z",
    },
  });
  assert.match(html, /c476d684-64e4-4392-a375-ade5bd64e251/);
  assert.match(html, /Copiar código/);
});

test("only pending received invitations can be accepted or declined", () => {
  const pending = {
    id: "inv-1",
    state: "PENDING",
    expiresAt: "2026-12-04T09:30:12Z",
  };
  const html = render({
    subscription: free,
    household: null,
    received: [pending],
  });
  assert.match(html, /<button[^>]*>Aceitar<\/button>/);
  assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*>Aceitar<\/button>/);
  const answered = {
    id: "inv-2",
    state: "ACCEPTED",
    expiresAt: "2026-12-04T09:30:12Z",
  };
  const done = render({
    subscription: free,
    household: null,
    received: [answered],
  });
  assert.match(done, /<button[^>]*disabled=""[^>]*>Aceitar<\/button>/);
  assert.match(done, /<button[^>]*disabled=""[^>]*>Recusar<\/button>/);
});

test("premium holder without family is offered to create one", () => {
  const html = render({ subscription: active, household: null });
  assert.match(html, /Crie sua família/);
  assert.match(html, /Criar família/);
  assert.doesNotMatch(html, /Assinar Premium Família/);
});

test("member sees the membership state and no holder controls", () => {
  const html = render({ subscription: free, household: member });
  assert.match(html, /Você é membro de uma família Premium/);
  assert.match(html, /2 de 5 vagas ocupadas ou reservadas/);
  assert.doesNotMatch(html, /Convidar alguém/);
  assert.doesNotMatch(html, /Remover/);
  assert.doesNotMatch(html, /Gerar código/);
});

test("holder sees members, seats, invite form and cancel subscription", () => {
  const html = render({ subscription: active, household: owner });
  assert.match(html, /1 de 5 vagas ocupadas ou reservadas/);
  assert.match(html, /Membro · Ativo/);
  assert.match(html, /Convidar alguém/);
  assert.match(html, /Cancelar assinatura/);
  assert.doesNotMatch(html, /Gerar código/);
});

test("error and notice messages are rendered for the user", () => {
  const html = render({
    subscription: free,
    household: null,
    error: "Falha de teste",
    notice: "Tudo certo",
  });
  assert.match(html, /role="alert">Falha de teste/);
  assert.match(html, /role="status">Tudo certo/);
});
