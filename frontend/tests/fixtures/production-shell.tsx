import React from "react";
import { createRoot } from "react-dom/client";
import { AuthenticatedShell } from "../../src/app/AuthenticatedShell";
import { ThemeProvider } from "@bookrush/ui/theme";
import { Button } from "@bookrush/ui/Button";
import "../../src/styles.css";
import "@bookrush/ui/styles.css";
import "../../src/styles/reader-shell.css";
import "../../src/styles/reader-refinements.css";
import "@bookrush/ui/sidebar.css";
import "@bookrush/ui/layout.css";

// Exercise the production legacy route and its real navigation adapter without
// contacting identity or backend services. No alternate sidebar is rendered.
document.body.dataset.start = "profile";
createRoot(document.getElementById("root")!).render(
  <ThemeProvider>
    <AuthenticatedShell
      error=""
      feedReady={false}
      legacyVisible={true}
      legacyQuery=""
      readerTab="feed"
      onRetry={() => {}}
      onShowLegacy={() => {}}
    />
    <Button>Verificar componente</Button>
  </ThemeProvider>,
);
const script = document.createElement("script");
script.src = "/pageloop.js";
document.body.appendChild(script);
