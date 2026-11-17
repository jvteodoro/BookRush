import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { bookrushUi } from "@bookrush/ui/vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  if (mode === "production" && env.VITE_USE_MOCKS === "true") {
    throw new Error("VITE_USE_MOCKS=true is forbidden in production builds");
  }
  return {
    plugins: [react(), bookrushUi()],
    resolve: { preserveSymlinks: true, dedupe: ["react", "react-dom"] },
    base: env.VITE_BASE_PATH || "/",
    server: { port: 5173 },
  };
});
