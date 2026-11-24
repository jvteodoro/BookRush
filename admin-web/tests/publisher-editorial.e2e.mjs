import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import process from "node:process";
import { createServer } from "node:http";
import { fileURLToPath, URL } from "node:url";
import { test } from "node:test";
import { build } from "esbuild";
import { chromium } from "playwright";

test("editorial queue approves after retry and owner can withdraw", async () => {
  const bundle = await build({
    stdin: {
      contents: `import React from'react';import{createRoot}from'react-dom/client';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{PublisherStatisticsPage}from'./src/features/publisher/PublisherStatisticsPage';import{setAccessToken}from'./src/lib/http';setAccessToken('editor-fixture');createRoot(document.getElementById('root')).render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><PublisherStatisticsPage/></QueryClientProvider>);`,
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
  let status = "PROCESSING",
    fail = true;
  const requests = [];
  const server = createServer(async (req, res) => {
    if (req.url === "/bundle.js") {
      res.setHeader("Content-Type", "application/javascript");
      res.end(bundle.outputFiles[0].text);
      return;
    }
    if (!req.url.startsWith("/api/")) {
      res.setHeader("Content-Type", "text/html");
      res.end('<div id="root"></div><script src="/bundle.js"></script>');
      return;
    }
    let body = Buffer.alloc(0);
    for await (const chunk of req) body = Buffer.concat([body, chunk]);
    requests.push({
      path: req.url,
      authorization: req.headers.authorization,
      body: body.toString(),
    });
    res.setHeader("Content-Type", "application/json");
    if (req.url === "/api/v1/publisher/submissions") {
      res.end(
        JSON.stringify([{ id: "owned", title: "Editorial fixture", status }]),
      );
      return;
    }
    if (req.url === "/api/v1/publisher/review") {
      res.end(
        JSON.stringify(
          status === "PROCESSING"
            ? [{ submission_id: "owned", title: "Editorial fixture" }]
            : [],
        ),
      );
      return;
    }
    if (req.url.endsWith("/review")) {
      if (fail) {
        fail = false;
        res.statusCode = 503;
      } else status = "PUBLISHED";
      res.end("{}");
      return;
    }
    if (req.url.endsWith("/withdraw")) {
      status = "WITHDRAWN";
      res.end("{}");
      return;
    }
    res.end(JSON.stringify({ status: "NOT_LINKED", days: 30, items: [] }));
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_PATH || "/usr/bin/chromium",
    headless: true,
    args: ["--no-sandbox"],
  });
  try {
    const page = await browser.newPage();
    await page.goto(`http://127.0.0.1:${server.address().port}`);
    await page.getByRole("button", { name: "Aprovar publicação" }).click();
    await page
      .getByRole("alert")
      .filter({ hasText: "Não foi possível atualizar a publicação" })
      .waitFor();
    await page.getByRole("button", { name: "Aprovar publicação" }).click();
    await page.getByRole("button", { name: "Retirar publicação" }).waitFor();
    assert.equal(status, "PUBLISHED");
    await page.getByRole("button", { name: "Retirar publicação" }).click();
    await page
      .getByRole("button", { name: "Retirar publicação" })
      .waitFor({ state: "detached" });
    assert.equal(status, "WITHDRAWN");
    assert.ok(
      requests.every(
        (request) => request.authorization === "Bearer editor-fixture",
      ),
    );
    assert.equal(
      JSON.parse(
        requests.find(
          (request) => request.path.endsWith("/review") && request.body,
        ).body,
      ).status,
      "APPROVED",
    );
  } finally {
    await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
});
