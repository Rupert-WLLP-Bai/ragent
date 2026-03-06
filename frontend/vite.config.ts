import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:9090",
        changeOrigin: true,
        secure: false
      }
    }
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: true,
    globals: true,
    coverage: {
      provider: "v8",
      include: [
        "src/pages/LoginPage.tsx",
        "src/components/chat/FeedbackButtons.tsx",
        "src/components/chat/MessageItem.tsx",
        "src/hooks/useStreamResponse.ts",
        "src/stores/authStore.ts",
        "src/utils/helpers.ts",
        "src/utils/storage.ts"
      ],
      reporter: ["text", "html", "json-summary"],
      reportsDirectory: "./coverage"
    }
  }
});
