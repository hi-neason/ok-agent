import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AuthProvider, installAuthenticatedFetch } from "./modules/auth";
import "./i18n";
import "./styles.css";
import "./model.css";
import "./llm-provider.css";
import "./skill.css";
import "./mcp.css";
import "./observe.css";
import "./confirm-dialog.css";
import "./auth.css";

installAuthenticatedFetch();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>,
);
