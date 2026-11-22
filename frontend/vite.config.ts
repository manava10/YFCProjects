import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const target =
    loadEnv(mode, ".", "").YFC_API_TARGET || "http://127.0.0.1:8080";
  return {
    plugins: [react()],
    server: { proxy: { "/api": target, "/login": target, "/logout": target } },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes("node_modules")) return;
            if (id.includes("react-router")) return "vendor-router";
            if (id.includes("@tanstack")) return "vendor-query";
            if (id.includes("lucide-react")) return "vendor-icons";
            if (id.includes("react") || id.includes("scheduler"))
              return "vendor-react";
          },
        },
      },
    },
  };
});
