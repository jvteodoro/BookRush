export const config = {
  basePath: import.meta.env.BASE_URL ?? '/',
  useMocks: (import.meta.env.VITE_USE_MOCKS ?? 'true') === 'true',
  keycloak: {
    url: import.meta.env.VITE_KEYCLOAK_URL ?? 'https://keycloak-bookrush.jteodoro.tec.br',
    realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'bookrush-platform',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'labsoft-admin-web',
  },
  apis: {
    catalog: import.meta.env.VITE_CATALOG_API_URL ?? '/api',
    ingestion: import.meta.env.VITE_INGESTION_API_URL ?? '/ingestion',
    analytics: import.meta.env.VITE_ANALYTICS_API_URL ?? '/analytics',
    feed: import.meta.env.VITE_FEED_API_URL ?? '/feed',
  },
};
