const envOr = (value: string | undefined, fallback: string) =>
  value?.trim() || fallback;

export const config = {
  basePath: import.meta.env.BASE_URL ?? "/",
  useMocks: envOr(import.meta.env.VITE_USE_MOCKS, "false") === "true",
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
    feed: envOr(import.meta.env.VITE_FEED_API_URL, "/feed"),
  },
};
