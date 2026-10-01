# LabSoft Admin Web

Console administrativa oficial para operar o catálogo, importações, analytics
de conteúdo e sessões de calibração do feed. A SPA não possui regras de
negócio próprias: mutações são enviadas aos serviços, autenticadas pelo
Keycloak e auditadas pelos backends.

## Execução

```bash
cp .env.example .env
npm ci
npm run dev
```

Para validar a imagem:

```bash
docker build -t bookrush/admin-web:local .
docker compose -f ../infrastructure/compose.yaml --env-file ../.env up -d admin-web
```

## Módulos

- **Catalog & Import** consulta o catálogo paginado e dispara ingestão com
  `Idempotency-Key`, acompanhando o job até concluir.
- **Analytics** dispara jobs versionados e apresenta o estado persistido.
- **Feed Lab** cria uma sessão `ADMIN_SEED`, registra impression/dwell e ações
  com `eventId`, cria versões imutáveis de parâmetros e só permite promoção a
  `feed_admin`.

Em `VITE_USE_MOCKS=true` os adapters são determinísticos e não fazem rede,
permitindo desenvolvimento offline. Em produção, use os caminhos do gateway
(`/api`, `/ingestion`, `/analytics` e `/feed`) e OIDC real.
