import process from "node:process";
import { Buffer } from "node:buffer";
import assert from "node:assert/strict";
import { test } from "node:test";
import { createServer } from "node:http";
import { createHash } from "node:crypto";
import { URL, fileURLToPath } from "node:url";
import { build } from "esbuild";
import { chromium } from "playwright";

test("publisher saves and resumes a draft, retries upload and submission, and keeps bearer away from storage", async () => {
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
  const books = [];
  const requests = [];
  let uploadAttempts = 0,
    submitAttempts = 0;
  const bytes = Buffer.from("fixture EPUB book");
  const hash = createHash("sha256").update(bytes).digest("hex");
  const server = createServer(async (req, res) => {
    if (req.url === "/bundle.js") {
      res.setHeader("Content-Type", "application/javascript");
      res.end(bundle.outputFiles[0].text);
      return;
    }
    if (!req.url.startsWith("/api/") && req.url !== "/staging-object") {
      res.setHeader("Content-Type", "text/html");
      res.end(
        '<!doctype html><meta charset="UTF-8"><div id="root"></div><script src="/bundle.js"></script>',
      );
      return;
    }
    let body = Buffer.alloc(0);
    for await (const chunk of req) body = Buffer.concat([body, chunk]);
    requests.push({
      url: req.url,
      method: req.method,
      authorization: req.headers.authorization,
      type: req.headers["content-type"],
      body,
    });
    res.setHeader("Content-Type", "application/json");
    if (req.url === "/staging-object") {
      uploadAttempts++;
      res.statusCode = uploadAttempts === 1 ? 500 : 200;
      res.end("{}");
      return;
    }
    if (req.url === "/api/v1/publisher/submissions") {
      if (req.method === "POST") {
        books.push({
          id: "draft",
          title: JSON.parse(body).title,
          status: "DRAFT",
        });
        res.end(JSON.stringify(books[0]));
      } else res.end(JSON.stringify(books));
      return;
    }
    if (req.url.endsWith("/metrics?days=30")) {
      res.end(JSON.stringify({ status: "NOT_LINKED", days: 30, items: [] }));
      return;
    }
    if (req.url.endsWith("/upload")) {
      res.end(
        JSON.stringify({
          uploadId: "upload",
          url: `http://127.0.0.1:${server.address().port}/staging-object`,
        }),
      );
      return;
    }
    if (req.url.endsWith("/finalize")) {
      books[0].status = "UPLOADED";
      res.end(JSON.stringify({ status: "FINALIZED" }));
      return;
    }
    if (req.url.endsWith("/rights")) {
      res.end("{}");
      return;
    }
    if (req.url.endsWith("/submit")) {
      submitAttempts++;
      if (submitAttempts === 1) {
        res.statusCode = 503;
        res.end("{}");
      } else {
        books[0].status = "PROCESSING";
        res.end(JSON.stringify({ id: "draft", status: "PROCESSING" }));
      }
      return;
    }
    res.statusCode = 404;
    res.end("{}");
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
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto(`http://127.0.0.1:${server.address().port}`);
    await page.getByLabel("Título do livro").fill("   ");
    assert.equal(
      await page.getByRole("button", { name: "Salvar rascunho" }).isDisabled(),
      true,
    );
    await page.getByLabel("Título do livro").fill("  Meu livro  ");
    await page.getByRole("button", { name: "Salvar rascunho" }).click();
    await page
      .getByText("Rascunho salvo. Agora anexe o arquivo do livro.")
      .waitFor();
    await page.reload();
    await page.getByLabel("Continuar rascunho").waitFor();
    assert.equal(
      await page
        .getByRole("button", { name: "Enviar para revisão" })
        .isDisabled(),
      true,
    );
    await page.getByLabel("Arquivo do livro (EPUB ou PDF)").setInputFiles({
      name: "book.txt",
      mimeType: "text/plain",
      buffer: bytes,
    });
    await page
      .getByRole("alert")
      .filter({ hasText: "Escolha um livro em EPUB ou PDF." })
      .waitFor();
    const file = { name: "book.epub", mimeType: "", buffer: bytes };
    await page.getByLabel("Arquivo do livro (EPUB ou PDF)").setInputFiles(file);
    await page
      .getByRole("button", { name: "Enviar arquivo", exact: true })
      .click();
    await page
      .getByRole("alert")
      .filter({ hasText: "Não foi possível enviar o arquivo" })
      .waitFor();
    assert.equal(requests.filter((r) => r.url.endsWith("/finalize")).length, 0);
    await page
      .getByRole("button", { name: "Enviar arquivo", exact: true })
      .click();
    await page
      .getByText(
        "Arquivo recebido e confirmado. Seu livro está pronto para envio à revisão.",
      )
      .waitFor();
    await page.getByRole("checkbox").check();
    await page.getByRole("button", { name: "Enviar para revisão" }).click();
    await page
      .getByRole("alert")
      .filter({ hasText: "O serviço está indisponível" })
      .waitFor();
    await page.getByRole("button", { name: "Enviar para revisão" }).click();
    await page
      .getByText(
        "Livro enviado para processamento e revisão. A publicação depende da aprovação editorial.",
      )
      .waitFor();
    assert.equal(books.length, 1);
    assert.equal(books[0].title, "Meu livro");
    assert.equal(books[0].status, "PROCESSING");
    assert.equal(
      JSON.parse(requests.find((r) => r.url.endsWith("/finalize")).body).sha256,
      hash,
    );
    assert.ok(
      requests
        .filter((r) => r.url.startsWith("/api/"))
        .every((r) => r.authorization === "Bearer test-token"),
    );
    assert.ok(
      requests
        .filter((r) => r.url === "/staging-object")
        .every(
          (r) =>
            !r.authorization &&
            r.type === "application/epub+zip" &&
            r.body.equals(bytes),
        ),
    );
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
});
