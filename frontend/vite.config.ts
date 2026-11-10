import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { bookrushUi } from "@bookrush/ui/vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "BOOKRUSH_");
  return {
    plugins: [react(), bookrushUi()],
    resolve: { preserveSymlinks: true, dedupe: ["react", "react-dom"] },
    server: {
      // This origin is already allowlisted by the product Keycloak client.
      port: 18080,
      strictPort: true,
      proxy: {
        "/api": {
          target: env.BOOKRUSH_API_PROXY_TARGET || "http://localhost:18081",
          changeOrigin: true,
        },
      },
    },
  };
});
