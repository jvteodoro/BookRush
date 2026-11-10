import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

const metadata = JSON.parse(
  readFileSync(new URL("./package.json", import.meta.url), "utf8"),
);
// Resolve to the live source rather than an optimized node_modules copy so
// changes in this package update both applications during development.
const alias = Object.entries(metadata.exports)
  .filter(([key]) => key !== "./vite")
  .map(([key, target]) => {
    const name = key === "." ? "@bookrush/ui" : `@bookrush/ui/${key.slice(2)}`;
    const pattern = name
      .replace(/[.+?^${}()|[\]\\]/g, "\\$&")
      .replace("*", "(.*)");
    return {
      find: new RegExp(`^${pattern}$`),
      replacement: fileURLToPath(new URL(target, import.meta.url)).replace(
        "*",
        "$1",
      ),
    };
  });

export function bookrushUi() {
  return {
    name: "bookrush-ui-source",
    configureServer(server) {
      // Exports are read when Vite loads its config. Rebuild that config when
      // the package adds a new entry instead of keeping the old wildcard alias.
      const manifest = fileURLToPath(
        new URL("./package.json", import.meta.url),
      );
      server.watcher.add(manifest);
      server.watcher.on("change", (path) => {
        if (resolve(path) === resolve(manifest)) void server.restart();
      });
    },
    config(config) {
      return {
        resolve: { alias, dedupe: ["react", "react-dom"] },
        optimizeDeps: { exclude: ["@bookrush/ui"] },
        server: {
          fs: {
            allow: [
              resolve(config.root || process.cwd()),
              fileURLToPath(new URL(".", import.meta.url)),
            ],
          },
        },
      };
    },
  };
}
