import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { fileURLToPath, URL } from "node:url";
import process from "node:process";
import console from "node:console";
/* global window, sessionStorage, Event */
import { createServer } from "vite";

const base = fileURLToPath(new URL("../../", import.meta.url));
const { chromium } = createRequire(
  new URL("../../../admin-web/package.json", import.meta.url),
)("playwright");
const server = await createServer({
  root: base,
  server: {
    host: "127.0.0.1",
    port: 21000 + Math.floor(Math.random() * 20000),
    hmr: false,
  },
});
await server.listen();
let browser;
try {
  browser = await chromium.launch({
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
    args: ["--no-sandbox"],
  });
  const page = await browser.newPage();
  const address = server.httpServer.address();
  const authority = "https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush";
  const productPaths = [];
  let state = "MINOR_AWAITING_AUTHORIZATION";
  let user = "restricted-fixture";
  await page.addInitScript(
    ({ oidc }) => {
      sessionStorage.setItem(
        `oidc.user:${oidc}:bookrush-web`,
        JSON.stringify({
          access_token: `synthetic-access-${window.name || "first"}`,
          token_type: "Bearer",
          expires_at: Math.floor(Date.now() / 1000) + 3600,
          profile: { sub: window.name || "first", iss: oidc },
        }),
      );
    },
    { oidc: authority },
  );
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/session") {
      if (state === "error") return route.fulfill({ status: 503, json: {} });
      const username = route
        .request()
        .headers()
        .authorization.endsWith("-second")
        ? "second-account"
        : user;
      const authorized = state === "MINOR_AUTHORIZED";
      return route.fulfill({
        json: {
          username,
          accessState: state,
          reasonCode: authorized ? null : "SUPERVISION_REQUIRED",
          capabilities: authorized
            ? ["SESSION_VIEW", "INVITE_SELF", "PRODUCT_USE"]
            : ["SESSION_VIEW", "INVITE_SELF"],
          validUntil: new Date(Date.now() + 60_000).toISOString(),
        },
      });
    }
    if (path === "/api/v1/family/invitations/me")
      return route.fulfill({
        json: [
          {
            id: "fixture",
            state: "PENDING",
            expiresAt: new Date(Date.now() + 60_000).toISOString(),
          },
        ],
      });
    if (path.endsWith("/fixture/accept")) return route.fulfill({ json: {} });
    productPaths.push(path);
    if (path.endsWith("/feed")) return route.fulfill({ json: { items: [] } });
    return route.fulfill({
      json: path.endsWith("/library") || path.endsWith("/recent") ? [] : {},
    });
  });
  await page.goto(`http://127.0.0.1:${address.port}/usuarios/fixture`);
  await page
    .getByRole("heading", { name: "Olá, restricted-fixture" })
    .waitFor();
  await page.getByRole("button", { name: "Aceitar" }).waitFor();
  assert.deepEqual(productPaths, []);
  state = "MINOR_AUTHORIZED";
  const start = page.waitForRequest((request) =>
    request.url().includes("/api/v1/reader/feed"),
  );
  await page.getByRole("button", { name: "Aceitar" }).click();
  await start;
  state = "error";
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await page
    .getByText("Não foi possível verificar seu acesso. Tente novamente.")
    .waitFor();
  assert.equal(await page.locator("#app").count(), 0);
  state = "MINOR_AWAITING_AUTHORIZATION";
  user = "current-fixture";
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await page.getByRole("heading", { name: "Olá, current-fixture" }).waitFor();
  assert.equal(await page.getByText("Olá, restricted-fixture").count(), 0);
  await page.evaluate(() => {
    window.name = "second";
  });
  await page.reload();
  await page.getByRole("heading", { name: "Olá, second-account" }).waitFor();
  assert.equal(await page.getByText("Olá, current-fixture").count(), 0);
  await page.route(`${authority}/**`, (route) => route.abort());
  await page.getByRole("button", { name: "Sair da conta" }).click();
  assert.equal(
    await page.evaluate(
      (oidc) => sessionStorage.getItem(`oidc.user:${oidc}:bookrush-web`),
      authority,
    ),
    null,
  );
  console.log(
    "Browser: deep link, bootstrap restrito, aceite, foco, 503 e limpeza da experiência de produto",
  );
} finally {
  await browser?.close();
  await server.close();
}
