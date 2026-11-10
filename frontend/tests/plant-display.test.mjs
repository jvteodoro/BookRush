import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { URL, fileURLToPath } from "node:url";

const bundle = await build({
  stdin: {
    contents: `
      import React from "react";
      import { renderToStaticMarkup } from "react-dom/server";
      import { AccountPage } from "./src/reader/AccountPages.tsx";
      export function renderPlant(plant) {
        return renderToStaticMarkup(React.createElement(AccountPage, {
          tab: "plant", userName: "Leitor", savedCount: 42, streak: 3,
          plant, onNavigate: () => {}
        }));
      }
    `,
    resolveDir: fileURLToPath(new URL("..", import.meta.url)),
    loader: "jsx",
  },
  plugins: [
    {
      name: "stub-images",
      setup(build) {
        build.onLoad({ filter: /\.png$/ }, () => ({
          contents: 'export default "plant.png"',
        }));
      },
    },
  ],
  bundle: true,
  preserveSymlinks: true,
  jsx: "automatic",
  define: { "import.meta.env": "{}" },
  write: false,
  format: "esm",
  platform: "browser",
});
globalThis.window = { location: { origin: "http://localhost" } };
globalThis.localStorage = {
  getItem: () => null,
  setItem: () => {},
  removeItem: () => {},
};
const { renderPlant } = await import(
  `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString("base64")}`
);

test("a planta mostra o tempo diário completo e mantém a meta e o estágio", () => {
  for (const [minutes, formatted] of [
    [0, "0 minutos"],
    [15, "15 minutos"],
    [25, "25 minutos"],
    [60, "1h00min"],
    [75, "1h15min"],
  ]) {
    const html = renderPlant({
      todaySeconds: minutes * 60 + 59,
      goalReached: minutes >= 15,
      stage: 2,
      totalReadingMinutes: 987,
    });
    assert.match(html, new RegExp(`Hoje: ${formatted} de 15 minutos`));
    assert.equal(html.includes("meta cumprida!"), minutes >= 15);
    assert.match(html, /estágio da planta/);
    assert.doesNotMatch(html, /livros salvos|min lidos|987/);
  }
});
