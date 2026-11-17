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

Em produção `VITE_USE_MOCKS=false` é obrigatório; os dados são consultados pelas APIs autenticadas. Mocks só podem ser habilitados explicitamente em desenvolvimento local.

`VITE_GRAFANA_URL` configura somente o link autenticado para o dashboard
operacional. O navegador não recebe tokens ou credenciais do Grafana; o SSO do
Grafana aplica a autorização de seus painéis.

## Dashboards administrativos — especificação (#8)

O centro de controle V2 apresenta seis áreas. Cada área consulta as fontes sob demanda e organiza indicadores, problemas/oportunidades, gráficos e detalhes expansíveis. Populações históricas indeterminadas são `UNKNOWN`; ausência de dados não vira zero. As definições dos 36 painéis e lacunas de fonte ficam no catálogo.

- [Especificação funcional e arquitetura](dashboard-specification.md)
- [Catálogo dos 36 painéis](dashboard-panel-catalog.md)
- [Contratos de dados, definições e atribuição](dashboard-data-contracts.md)
- [Implementação, validação e limitações](dashboard-implementation-plan.md)
- [Issue GitHub #8](https://github.com/jvteodoro/BookRush/issues/8)
