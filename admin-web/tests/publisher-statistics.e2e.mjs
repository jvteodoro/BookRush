import process from "node:process";
import assert from "node:assert/strict";
import { test } from "node:test";
import { createServer } from "node:http";
import { URL, fileURLToPath } from "node:url";
import { build } from "esbuild";
import { chromium } from "playwright";

test("publisher dashboard: totals, periods, unlinked publication and retry with bearer", async () => {
  const bundle = await build({
    stdin: {
      contents: `import React from 'react';import{createRoot}from'react-dom/client';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{PublisherStatisticsPage}from'./src/features/publisher/PublisherStatisticsPage';import{setAccessToken}from'./src/lib/http';setAccessToken('test-token');createRoot(document.getElementById('root')).render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><PublisherStatisticsPage/></QueryClientProvider>);`,
      resolveDir: fileURLToPath(new URL("../", import.meta.url)),
      loader: "tsx",
    },
    bundle: true,
    write: false,
    preserveSymlinks: true,
    jsx: "automatic",
    define: { "import.meta.env": "{}" },
    loader: { ".css": "empty" },
  });
  let fail = false;
  const requests = [];
  const row = {
    day: "2026-11-10",
    impressions: 12,
    opens: 4,
    reads: 3,
    likes: 2,
    shares: 1,
    comments: 2,
  };
  const server = createServer((req, res) => {
    if (req.url === "/bundle.js") {
      res.setHeader("Content-Type", "application/javascript");
      res.end(bundle.outputFiles[0].text);
      return;
    }
    if (req.url.startsWith("/api/")) {
      requests.push({ url: req.url, authorization: req.headers.authorization });
      res.setHeader("Content-Type", "application/json");
      if (fail) {
        res.statusCode = 503;
        res.end("{}");
        return;
      }
      if (req.url === "/api/v1/publisher/submissions") {
        res.end(
          JSON.stringify([
            { id: "owned", title: "Livro do publicador", status: "PUBLISHED" },
            { id: "draft", title: "Rascunho", status: "DRAFT" },
          ]),
        );
        return;
      }
      const unlinked = req.url.includes("/draft/");
      res.end(
        JSON.stringify({
          status: unlinked ? "NOT_LINKED" : "AVAILABLE",
          days: 30,
          items:
            unlinked || req.url.includes("days=7")
              ? []
              : [row, { ...row, day: "2026-11-09" }],
        }),
      );
      return;
    }
    res.setHeader("Content-Type", "text/html");
    res.end(
      '<!doctype html><meta charset="UTF-8"><div id="root"></div><script src="/bundle.js"></script>',
    );
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_PATH || "/usr/bin/chromium",
    headless: true,
    args: ["--no-sandbox"],
  });
  try {
    const page = await browser.newPage();
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    await page.goto(`http://127.0.0.1:${server.address().port}`);
    await page
      .locator('[aria-label="Totais do livro"]')
      .getByText("24", { exact: true })
      .waitFor();
    assert.equal(await page.getByRole("table").locator("tbody tr").count(), 2);
    await page.getByLabel("Período", { exact: true }).selectOption("7");
    await page
      .getByText("Nenhuma atividade registrada neste período.")
      .waitFor();
    await page.getByLabel("Livro", { exact: true }).selectOption("draft");
    await page.getByText(/Esta publicação ainda aguarda vínculo/).waitFor();
    fail = true;
    await page.getByRole("button", { name: "Atualizar estatísticas" }).click();
    await page.getByRole("alert").waitFor();
    assert.equal(
      await page.locator('[aria-label="Totais do livro"]').count(),
      0,
    );
    fail = false;
    await page.getByRole("button", { name: "Tentar novamente" }).click();
    await page.getByText(/Esta publicação ainda aguarda vínculo/).waitFor();
    assert.ok(requests.some((r) => r.url.includes("days=7")));
    assert.ok(requests.every((r) => r.authorization === "Bearer test-token"));
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
});
