// Node module hooks: run the mobile TypeScript/TSX sources in tests without a bundler.
import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const stub = new URL("./react-native-stub.mjs", import.meta.url).href;

export async function resolve(specifier, context, next) {
  if (specifier === "react-native") return { url: stub, shortCircuit: true };
  if (
    (specifier.startsWith("./") || specifier.startsWith("../")) &&
    !/\.[mc]?[jt]sx?$|\.json$/.test(specifier)
  ) {
    for (const extension of [".ts", ".tsx"]) {
      const candidate = new URL(specifier + extension, context.parentURL);
      if (existsSync(fileURLToPath(candidate)))
        return { url: candidate.href, shortCircuit: true };
    }
  }
  return next(specifier, context);
}

export async function load(url, context, next) {
  if (url.endsWith(".ts") || url.endsWith(".tsx")) {
    const source = await readFile(new URL(url), "utf8");
    const { outputText } = ts.transpileModule(source, {
      fileName: url,
      compilerOptions: {
        module: ts.ModuleKind.ESNext,
        target: ts.ScriptTarget.ES2020,
        jsx: ts.JsxEmit.ReactJSX,
      },
    });
    return { format: "module", source: outputText, shortCircuit: true };
  }
  return next(url, context);
}
