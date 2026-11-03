import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { URL, fileURLToPath } from "node:url";

const bundle = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/appearancePanel.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const { mountAppearance } = await import(
  `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString("base64")}`
);

test("appearance controls apply, persist, restore and reflect the chosen options", () => {
  let saved;
  globalThis.localStorage = {
    getItem: () => saved,
    setItem: (_key, value) => {
      saved = value;
    },
  };
  const input = {
    value: "",
    addEventListener: (_type, handler) => {
      input.change = handler;
    },
  };
  const output = {};
  const status = {};
  const buttons = [
    { dataset: { font: "literary" } },
    { dataset: { spacing: "2.1" } },
    { dataset: { theme: "night" } },
    { dataset: { width: "wide" } },
  ].map((button) => ({
    ...button,
    setAttribute: (key, value) => {
      button[key] = value;
    },
    closest() {
      return this;
    },
  }));
  const panel = {
    innerHTML: "",
    querySelector: (selector) =>
      selector === "#readerFontSize"
        ? input
        : selector === "output"
          ? output
          : status,
    querySelectorAll: (selector) =>
      selector === ".reader-options" ? [] : buttons,
    closest: () => ({ style: { setProperty() {} } }),
    addEventListener: (_type, handler) => {
      panel.click = handler;
    },
  };
  const applied = [];
  mountAppearance(panel, (value) => applied.push({ ...value }));
  for (const button of buttons) panel.click({ target: button });
  input.value = "135";
  input.change();
  assert.deepEqual(applied.at(-1), {
    fontFamily: "literary",
    fontSize: 135,
    lineHeight: 2.1,
    textWidth: "wide",
    background: "#1b1b1b",
    foreground: "#f7f4ee",
  });
  assert.deepEqual(JSON.parse(saved), applied.at(-1));
  assert.equal(output.textContent, "135%");
  panel.click({
    target: {
      closest: () => ({
        dataset: {},
        classList: { contains: (value) => value === "reader-reset" },
      }),
    },
  });
  assert.equal(applied.at(-1).fontSize, 100);
  assert.equal(applied.at(-1).fontFamily, "serif");
  assert.equal(applied.at(-1).textWidth, "narrow");
  delete globalThis.localStorage;
});
