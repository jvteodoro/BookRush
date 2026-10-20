import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { URL, fileURLToPath } from "node:url";

const bundled = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/readerPanels.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const { mountReaderPanels } = await import(
  `data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`
);

function setup() {
  const node = (id) => ({
    id,
    hidden: true,
    dataset: {},
    attributes: {},
    listeners: {},
    focus() {
      this.focused = true;
    },
    addEventListener(key, listener) {
      this.listeners[key] = listener;
    },
    setAttribute(key, value) {
      this.attributes[key] = value;
    },
  });
  const nodes = {};
  for (const name of ["toc", "settings"]) {
    const close = node(`${name}Close`);
    const panel = node(`${name}Panel`);
    panel.querySelector = () => close;
    nodes[`#${name}Panel`] = panel;
    nodes[`#${name}Btn`] = node(`${name}Btn`);
    nodes[`#${name}Close`] = close;
  }
  const controller = mountReaderPanels({
    querySelector: (selector) => nodes[selector],
  });
  return { nodes, controller };
}

test("each toolbar button toggles its panel and rapid clicks preserve the final state", () => {
  const { nodes } = setup();
  for (const name of ["toc", "settings"]) {
    const button = nodes[`#${name}Btn`];
    const panel = nodes[`#${name}Panel`];
    assert.equal(panel.inert, true);
    button.listeners.click();
    assert.equal(panel.dataset.open, "true");
    assert.equal(button.attributes["aria-expanded"], "true");
    button.listeners.click();
    assert.equal(panel.dataset.open, "false");
    assert.equal(panel.inert, true);
    button.listeners.click();
    button.listeners.click();
    button.listeners.click();
    assert.equal(panel.dataset.open, "true");
    button.listeners.click();
  }
});

test("switching panels closes the previous one; Escape and chapter navigation restore focus", () => {
  const { nodes, controller } = setup();
  nodes["#tocBtn"].listeners.click();
  nodes["#settingsBtn"].listeners.click();
  assert.equal(nodes["#tocPanel"].dataset.open, "false");
  assert.equal(nodes["#tocBtn"].attributes["aria-expanded"], "false");
  assert.equal(nodes["#settingsPanel"].inert, false);
  nodes["#settingsPanel"].listeners.keydown({
    key: "Escape",
    preventDefault() {},
  });
  assert.equal(nodes["#settingsPanel"].dataset.open, "false");
  assert.equal(nodes["#settingsBtn"].focused, true);
  nodes["#tocBtn"].listeners.click();
  controller.close();
  assert.equal(nodes["#tocPanel"].dataset.open, "false");
  assert.equal(nodes["#tocBtn"].focused, true);
});
