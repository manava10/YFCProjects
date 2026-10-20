import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig(({ mode }) => {
  const target =
    loadEnv(mode, ".", "").YFC_API_TARGET || "http://127.0.0.1:8080";
  return {
    plugins: [react()],
    server: { proxy: { "/api": target, "/login": target, "/logout": target } },
  };
});
