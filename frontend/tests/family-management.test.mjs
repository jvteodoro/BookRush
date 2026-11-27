import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { test } from "node:test";
import { URL, fileURLToPath } from "node:url";
import { build } from "esbuild";

const result = await build({
  entryPoints: [
    fileURLToPath(new URL("../../packages/session/family.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  platform: "browser",
  format: "esm",
});
const family = await import(
  `data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString("base64")}`
);

const json = (body, status = 200) => ({
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

const household = {
  id: "049f2dee-e09f-4c15-8137-b803c95e700e",
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

test("recipient codes must be UUIDs and seats count free places", () => {
  assert.equal(
    family.isRecipientCode("c476d684-64e4-4392-a375-ade5bd64e251"),
    true,
  );
  assert.equal(
    family.isRecipientCode("  c476d684-64e4-4392-a375-ade5bd64e251 "),
    true,
  );
  assert.equal(family.isRecipientCode("não é um código"), false);
  assert.equal(family.isRecipientCode(""), false);
  assert.equal(family.freeSeats(household), 4);
  assert.equal(family.freeSeats({ ...household, occupiedOrReserved: 9 }), 0);
});

test("a 404 on /family/me means the account has no household yet", async () => {
  const { request, calls } = recorder([json({ code: "NOT_FOUND" }, 404)]);
  assert.equal(await family.loadHousehold(request), null);
  assert.equal(calls[0].path, "/api/v1/family/me");
});

test("loadHousehold returns the household on success", async () => {
  const { request } = recorder([json(household)]);
  assert.deepEqual(await family.loadHousehold(request), household);
});

test("invite sends the recipient code with an idempotency key and trims it", async () => {
  const invitation = {
    id: "c476d684-64e4-4392-a375-ade5bd64e251",
    householdId: household.id,
    state: "PENDING",
    expiresAt: "2026-12-04T09:30:12Z",
  };
  const { request, calls } = recorder([json(invitation, 201)]);
  const key = "7d4f4d3e-1111-4222-8333-944444444444";
  const created = await family.inviteMember(
    request,
    household.id,
    " c476d684-64e4-4392-a375-ade5bd64e251 ",
    key,
  );
  assert.deepEqual(created, invitation);
  assert.equal(calls[0].method, "POST");
  assert.equal(calls[0].path, `/api/v1/family/${household.id}/invitations`);
  assert.equal(calls[0].headers["Idempotency-Key"], key);
  assert.equal(calls[0].headers["Content-Type"], "application/json");
  assert.deepEqual(JSON.parse(calls[0].body), {
    recipientCode: "c476d684-64e4-4392-a375-ade5bd64e251",
  });
});

test("removeMember sends the member version as expectedVersion", async () => {
  const { request, calls } = recorder([json(undefined, 204)]);
  await family.removeMember(request, household.id, household.members[0]);
  assert.equal(calls[0].method, "DELETE");
  assert.equal(
    calls[0].path,
    `/api/v1/family/${household.id}/members/${household.members[0].id}?expectedVersion=2`,
  );
});

test("cancelInvitation issues DELETE on the household invitation", async () => {
  const { request, calls } = recorder([json(undefined, 204)]);
  await family.cancelInvitation(
    request,
    household.id,
    "c476d684-64e4-4392-a375-ade5bd64e251",
  );
  assert.equal(calls[0].method, "DELETE");
  assert.equal(
    calls[0].path,
    `/api/v1/family/${household.id}/invitations/c476d684-64e4-4392-a375-ade5bd64e251`,
  );
});

test("failures map to user-facing messages instead of raw API bodies", async () => {
  const { request } = recorder([json({ detail: "FORBIDDEN_INTERNAL" }, 403)]);
  await assert.rejects(
    () => family.createHousehold(request),
    (cause) =>
      cause.message === family.familyMessage(403) &&
      !cause.message.includes("FORBIDDEN_INTERNAL"),
  );
  assert.match(family.familyMessage(409), /mudou de estado/);
  assert.match(family.familyMessage(500), /Não foi possível/);
});

test("newIdempotencyKey returns a version 4 UUID", () => {
  const key = family.newIdempotencyKey();
  assert.match(
    key,
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
  );
  assert.notEqual(family.newIdempotencyKey(), key);
});

test("member households are recognised and only pending invitations can be answered", () => {
  assert.equal(
    family.isMemberHousehold({
      id: "h",
      role: "MEMBER",
      additionalSeats: 5,
      occupiedOrReserved: 2,
      members: [],
    }),
    true,
  );
  assert.equal(
    family.isMemberHousehold({
      id: "h",
      role: "OWNER",
      additionalSeats: 5,
      occupiedOrReserved: 1,
      members: [],
    }),
    false,
  );
  assert.equal(
    family.canRespondToInvitation({
      id: "i",
      state: "PENDING",
      expiresAt: "2026-12-04T09:30:12Z",
    }),
    true,
  );
  assert.equal(
    family.canRespondToInvitation({
      id: "i",
      state: "ACCEPTED",
      expiresAt: "2026-12-04T09:30:12Z",
    }),
    false,
  );
});

test("guest actions use the same invitation endpoints as the restricted screen", async () => {
  const { request, calls } = recorder([
    json({
      code: "c476d684-64e4-4392-a375-ade5bd64e251",
      expiresAt: "2026-12-04T09:30:12Z",
    }),
    json({ state: "ok" }, 200),
    json(undefined, 204),
  ]);
  const address = await family.createInviteAddress(request);
  assert.equal(address.code, "c476d684-64e4-4392-a375-ade5bd64e251");
  assert.equal(calls[0].method, "POST");
  assert.equal(calls[0].path, "/api/v1/family/invite-address");
  await family.respondToInvitation(request, "inv-1", "accept");
  assert.equal(calls[1].path, "/api/v1/family/invitations/inv-1/accept");
  await family.respondToInvitation(request, "inv-2", "decline");
  assert.equal(calls[2].path, "/api/v1/family/invitations/inv-2/decline");
  assert.equal(calls[2].method, "POST");
});

test("received invitations are listed from the invitations endpoint", async () => {
  const inbox = [
    { id: "inv-1", state: "PENDING", expiresAt: "2026-12-04T09:30:12Z" },
  ];
  const { request, calls } = recorder([json(inbox)]);
  assert.deepEqual(await family.loadReceivedInvitations(request), inbox);
  assert.equal(calls[0].path, "/api/v1/family/invitations/me");
});

test("familyView decides the family screen state for every account situation", () => {
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
  assert.equal(family.familyView(premium, undefined), "loading");
  assert.equal(family.familyView(free, null), "guest");
  assert.equal(family.familyView(premium, null), "premium-without-family");
  assert.equal(family.familyView(premium, owner), "owner");
  assert.equal(family.familyView(free, member), "member");
  assert.equal(family.familyView(premium, member), "member");
});
