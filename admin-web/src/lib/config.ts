const envOr = (value: string | undefined, fallback: string) =>
  value?.trim() || fallback;

const useMocks = envOr(import.meta.env.VITE_USE_MOCKS, "false") === "true";
if (import.meta.env.PROD && useMocks) {
  throw new Error("VITE_USE_MOCKS=true is forbidden in production builds");
}

export const config = {
  basePath: import.meta.env.BASE_URL ?? "/",
  useMocks,
  keycloak: {
    url: envOr(
      import.meta.env.VITE_KEYCLOAK_URL,
      "https://keycloak-bookrush.jteodoro.tec.br",
    ),
    realm: envOr(import.meta.env.VITE_KEYCLOAK_REALM, "bookrush-platform"),
    clientId: envOr(
      import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
      "labsoft-admin-web",
    ),
  },
  apis: {
    catalog: envOr(import.meta.env.VITE_CATALOG_API_URL, "/api"),
    ingestion: envOr(import.meta.env.VITE_INGESTION_API_URL, "/ingestion"),
    analytics: envOr(import.meta.env.VITE_ANALYTICS_API_URL, "/analytics"),
    feed: envOr(import.meta.env.VITE_FEED_API_URL, "/api/v1"),
    admin: envOr(import.meta.env.VITE_ADMIN_API_URL, "/"),
  },
};
