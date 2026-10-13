FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_OIDC_AUTHORITY
ARG VITE_OIDC_CLIENT_ID=bookrush-web
ENV VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY
ENV VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
COPY package.json .
RUN npm install
COPY . .
RUN npm run format:check \
 && npm test \
 && npm run test:coverage \
 && npm run lint \
 && npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
