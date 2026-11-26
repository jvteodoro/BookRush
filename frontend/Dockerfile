FROM node:22-alpine AS build
WORKDIR /workspace/frontend
ARG VITE_OIDC_AUTHORITY=https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush
ARG VITE_OIDC_CLIENT_ID=bookrush-web
ENV VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY \
    VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
COPY packages/ui /workspace/packages/ui
COPY packages/session /workspace/packages/session
COPY eslint.shared.config.mjs /workspace/eslint.shared.config.mjs
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run format:check \
 && npm run format:ui \
 && npm test \
 && npm run test:coverage \
 && npm run lint \
 && npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
EXPOSE 8080
