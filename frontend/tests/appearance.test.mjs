import assert from "node:assert/strict";
import { test } from "node:test";
import { build } from "esbuild";
import { Buffer } from "node:buffer";
import { URL, fileURLToPath } from "node:url";

const bundle = await build({
  entryPoints: [
    fileURLToPath(new URL("../src/reader/appearance.ts", import.meta.url)),
  ],
  bundle: true,
  write: false,
  format: "esm",
  platform: "browser",
});
const {
  normalizePreferences,
  readPreferences,
  savePreferences,
  defaults,
  fonts,
  themes,
} = await import(
  `data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString("base64")}`
);

test("all theme pairs keep high text contrast and normalize without changing colors", () => {
  const luminance = (hex) => {
    const rgb = hex
      .slice(1)
      .match(/../g)
      .map((channel) => {
        const value = parseInt(channel, 16) / 255;
        return value <= 0.04045
          ? value / 12.92
          : ((value + 0.055) / 1.055) ** 2.4;
      });
    return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722;
  };
  for (const theme of Object.values(themes)) {
    const foreground = luminance(theme.foreground);
    const background = luminance(theme.background);
    const contrast =
      (Math.max(foreground, background) + 0.05) /
      (Math.min(foreground, background) + 0.05);
    assert.ok(contrast >= 7, `${theme.name} text contrast: ${contrast}`);
    const normalized = normalizePreferences({ background: theme.background });
    assert.equal(normalized.background, theme.background);
    assert.equal(normalized.foreground, theme.foreground);
  }
  for (const fontFamily of Object.keys(fonts)) {
    assert.equal(normalizePreferences({ fontFamily }).fontFamily, fontFamily);
  }
});

test("corrupt and legacy preferences recover with readable paired theme colors", () => {
  for (const value of [
    null,
    [],
    "invalid",
    { fontSize: "bad", lineHeight: Infinity },
  ]) {
    assert.deepEqual(normalizePreferences(value), defaults);
  }
  assert.deepEqual(
    normalizePreferences({
      fontFamily: "dyslexic",
      fontSize: 500,
      lineHeight: 2.4,
      background: "#1b1b1b",
      foreground: "#29261f",
    }),
    {
      fontFamily: "serif",
      fontSize: 170,
      lineHeight: 2.1,
      textWidth: "narrow",
      background: "#1b1b1b",
      foreground: "#f7f4ee",
    },
  );
  assert.equal(
    normalizePreferences({ fontSize: 92, lineHeight: 1.6 }).fontSize,
    90,
  );
  assert.equal(normalizePreferences({ lineHeight: 1.6 }).lineHeight, 1.5);
  assert.equal(normalizePreferences({ textWidth: "invalid" }).textWidth, "narrow");
  for (const textWidth of ["narrow", "comfortable", "wide"]) {
    assert.equal(normalizePreferences({ textWidth }).textWidth, textWidth);
  }
});

test("preferences survive reopening and storage failures do not interrupt reading", () => {
  let saved;
  globalThis.localStorage = {
    setItem: (_key, value) => {
      saved = value;
    },
    getItem: () => saved,
  };
  const value = normalizePreferences({
    fontFamily: "literary",
    fontSize: 125,
    lineHeight: 2.1,
    textWidth: "wide",
    background: "#f1e7c8",
  });
  assert.equal(savePreferences(value), true);
  assert.deepEqual(readPreferences(), value);
  globalThis.localStorage = {
    getItem: () => "{broken",
    setItem: () => {
      throw new Error("quota");
    },
  };
  assert.deepEqual(readPreferences(), defaults);
  assert.equal(savePreferences(value), false);
  delete globalThis.localStorage;
});
