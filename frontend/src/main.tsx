import React from "react";
import ReactDOM from "react-dom/client";

import App from "@/App";
import { setApiClientHandlers } from "@/services/api";
import { useAuthStore } from "@/stores/authStore";
import { useThemeStore } from "@/stores/themeStore";
import "@/styles/globals.css";

useThemeStore.getState().initialize();
setApiClientHandlers({
  onUnauthorized: () => {
    useAuthStore.getState().expireSession("session_expired");
  }
});
useAuthStore.getState().checkAuth();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
