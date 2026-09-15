FROM node:24-bookworm-slim AS dependencies
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-venv build-essential git ca-certificates chromium && rm -rf /var/lib/apt/lists/*
ENV PUPPETEER_SKIP_DOWNLOAD=true CHROME_BIN=/usr/bin/chromium
WORKDIR /repo/backstage
COPY backstage/package.json backstage/yarn.lock backstage/.yarnrc.yml ./
COPY backstage/.yarn .yarn
COPY backstage/packages/app/package.json packages/app/package.json
COPY backstage/packages/backend/package.json packages/backend/package.json
RUN node .yarn/releases/yarn-4.13.0.cjs install --immutable
RUN python3 -m venv /opt/docs
COPY backstage/requirements-docs.txt /tmp/requirements-docs.txt
RUN /opt/docs/bin/pip install --no-cache-dir -r /tmp/requirements-docs.txt
ENV PATH=/opt/docs/bin:/repo/backstage/node_modules/.bin:$PATH
FROM dependencies AS source
COPY backstage/ ./
COPY catalog-info.yaml mkdocs.yml /repo/
COPY docs /repo/docs
COPY platform /repo/platform
COPY examples/platform-consumer /repo/examples/platform-consumer
COPY infrastructure/secrets/README.md /repo/infrastructure/secrets/README.md
COPY services/catalog-service/catalog-info.yaml services/catalog-service/mkdocs.yml /repo/services/catalog-service/
COPY services/catalog-service/docs /repo/services/catalog-service/docs
COPY services/catalog-service/api /repo/services/catalog-service/api
COPY services/book-ingestion-service/catalog-info.yaml services/book-ingestion-service/mkdocs.yml /repo/services/book-ingestion-service/
COPY services/book-ingestion-service/docs /repo/services/book-ingestion-service/docs
COPY services/book-ingestion-service/api /repo/services/book-ingestion-service/api
COPY services/book-analytics-service/catalog-info.yaml services/book-analytics-service/mkdocs.yml /repo/services/book-analytics-service/
COPY services/book-analytics-service/docs /repo/services/book-analytics-service/docs
COPY services/book-analytics-service/api /repo/services/book-analytics-service/api
COPY frontend/catalog-info.yaml frontend/mkdocs.yml /repo/frontend/
COPY frontend/docs /repo/frontend/docs
FROM source AS validation
RUN node scripts/validate.mjs && node scripts/test-template.mjs
RUN python3 scripts/build-docs.py
RUN node .yarn/releases/yarn-4.13.0.cjs tsc --noEmit
RUN node .yarn/releases/yarn-4.13.0.cjs lint:all
RUN node .yarn/releases/yarn-4.13.0.cjs test --watchAll=false --passWithNoTests
FROM source AS build
RUN node scripts/prepare-local.mjs
RUN node .yarn/releases/yarn-4.13.0.cjs build:all && tar -xzf packages/backend/dist/bundle.tar.gz -C /repo/backstage
FROM build AS runtime
# The runtime includes MkDocs for isolated local builds; production uses external publishing.
# The deployed image always runs with the production frontend/auth profile.
# Local development uses `yarn dev` outside this runtime image.
ENV NODE_ENV=production
RUN chmod 0755 entrypoint.sh && mkdir -p /app/techdocs && chown -R node:node /app /repo/backstage
USER node
WORKDIR /repo/backstage/packages/backend
EXPOSE 7007
ENTRYPOINT ["/repo/backstage/entrypoint.sh"]
