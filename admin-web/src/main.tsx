import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./auth/AuthProvider";
import { App } from "./app/App";
import { ErrorBoundary } from "./app/ErrorBoundary";
import { ThemeProvider } from "@bookrush/ui/theme";
import "./styles/design-system.css";
import "./styles/app.css";
import "./styles/admin-layout.css";
import "@bookrush/ui/sidebar.css";
import "@bookrush/ui/layout.css";

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 15000, retry: 1 } },
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ThemeProvider defaultTheme="light">
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <App />
          </AuthProvider>
        </QueryClientProvider>
      </ErrorBoundary>
    </ThemeProvider>
  </React.StrictMode>,
);
