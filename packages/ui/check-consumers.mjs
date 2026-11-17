import { existsSync, readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = new URL("../../", import.meta.url);
const errors = [];
function scan(directory) {
  if (!existsSync(directory)) return;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = `${directory}/${entry.name}`;
    if (entry.isDirectory()) scan(path);
    else if (path.endsWith(".css")) {
      const css = readFileSync(path, "utf8").replace(/\/\*[\s\S]*?\*\//g, "");
      for (const match of css.matchAll(/([^{}]+)\{[^{}]*\}/g)) {
        if (
          /\.br-(?:sidebar(?:__[\w-]+|-toggle)?|nav-indicator|header(?:__[\w-]+)?|search-control|input)\b/.test(
            match[1],
          )
        )
          errors.push(
            `${path}: estilo de componente compartilhado fora de @bookrush/ui: ${match[1].trim()}`,
          );
      }
    }
  }
}
for (const app of ["frontend", "admin-web"])
  scan(fileURLToPath(new URL(`${app}/src/styles/`, root)));
if (errors.length) throw new Error(errors.join("\n"));
console.log(
  "UI compartilhada: nenhum override local dos componentes protegidos.",
);
