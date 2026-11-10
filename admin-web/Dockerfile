FROM node:22-alpine AS build
WORKDIR /workspace/admin-web
COPY packages/ui /workspace/packages/ui
COPY eslint.shared.config.mjs /workspace/eslint.shared.config.mjs
COPY admin-web/package*.json ./
RUN npm ci
COPY admin-web/ .
ARG VITE_USE_MOCKS=false
ARG VITE_KEYCLOAK_URL=https://keycloak-bookrush.jteodoro.tec.br
ARG VITE_KEYCLOAK_REALM=bookrush-platform
ARG VITE_KEYCLOAK_CLIENT_ID=labsoft-admin-web
ARG VITE_CATALOG_API_URL=/api
ARG VITE_INGESTION_API_URL=/ingestion
ARG VITE_ANALYTICS_API_URL=/analytics
ARG VITE_FEED_API_URL=/api/v1
ARG VITE_BASE_PATH=/admin/
ENV VITE_USE_MOCKS=$VITE_USE_MOCKS VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID VITE_CATALOG_API_URL=$VITE_CATALOG_API_URL VITE_INGESTION_API_URL=$VITE_INGESTION_API_URL VITE_ANALYTICS_API_URL=$VITE_ANALYTICS_API_URL VITE_FEED_API_URL=$VITE_FEED_API_URL VITE_BASE_PATH=$VITE_BASE_PATH
RUN npm run format:check \
 && npm run format:ui \
 && npm test \
 && npm run test:coverage \
 && npm run build \
 && npm run lint
FROM nginx:1.27-alpine
COPY admin-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/admin-web/dist /usr/share/nginx/html
EXPOSE 80
