import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { URL } from "node:url";
import process from "node:process";

const { chromium } = createRequire(
  new URL("../admin-web/package.json", import.meta.url),
)("playwright");
const base = process.env.BOOKRUSH_DEMO_BASE_URL ?? "http://localhost:19080";
const accounts = [
  {
    username: process.env.DEMO_ADULT_USERNAME,
    password: process.env.DEMO_ADULT_PASSWORD,
    kind: "adult",
  },
  {
    username: process.env.DEMO_MINOR_USERNAME,
    password: process.env.DEMO_MINOR_PASSWORD,
    kind: "restricted",
  },
];
if (accounts.some(({ username, password }) => !username || !password)) {
  throw new Error("Synthetic demo browser credentials are not configured");
}

const browser = await chromium.launch({ args: ["--no-sandbox"] });
try {
  for (const account of accounts) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const productRequests = [];
    const sessionResponse = page.waitForResponse(
      (response) => new URL(response.url()).pathname === "/api/v1/session",
      { timeout: 45000 },
    );
    page.on("request", (request) => {
      if (request.url().includes("/api/v1/reader/"))
        productRequests.push(new URL(request.url()).pathname);
    });
    try {
      await page.goto(base, { waitUntil: "domcontentloaded", timeout: 30000 });
      await page.getByRole("button", { name: /Entrar/ }).click();
      await page.waitForURL(/keycloak|realms\/bookrush/, { timeout: 30000 });
      await page.locator("#username").fill(account.username);
      await page.locator("#password").fill(account.password);
      await page.locator("#kc-login").click();
      await page.waitForURL((url) => url.origin === new URL(base).origin, {
        timeout: 45000,
      });
      const response = await sessionResponse;
      assert.equal(response.status(), 200, "Restricted accounts still receive a session snapshot");
      const session = await response.json();
      assert.ok(session, "Session endpoint was not consumed");
      if (account.kind === "adult") {
        assert.equal(session.accessState, "ADULT_ELIGIBLE");
        assert.ok(session.capabilities.includes("PRODUCT_USE"));
        await page.waitForTimeout(1500);
        assert.ok(productRequests.some((path) => path.includes("/feed")));
      } else {
        assert.match(session.accessState, /^MINOR_/);
        assert.ok(!session.capabilities.includes("PRODUCT_USE"));
        assert.ok(productRequests.length === 0);
        await page.getByRole("heading", { name: `Olá, ${account.username}` }).waitFor();
        await page.getByRole("heading", { name: "Convites familiares" }).waitFor();
      }
      await page.screenshot({
        path: `artifacts/premium-family-${account.kind}.png`,
        fullPage: true,
      });
    } catch {
      throw new Error(`Chromium demo flow failed for synthetic ${account.kind} account`);
    } finally {
      await context.close();
    }
  }
  console.log("Chromium: OIDC adulto, sessão gratuita e experiência restrita de menor OK");
} finally {
  await browser.close();
}
