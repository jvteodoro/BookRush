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

A área `/dashboards` já possui o hub React inicial com sete áreas, filtros de
período sincronizados na URL, estados acessíveis e integração read-only com
`/api/admin/v1/reporting/reader-day` e `/health`. A primeira fonte retorna
`UNTRUSTED` enquanto a população não estiver classificada; as demais áreas
mostram `NOT_INSTRUMENTED` até suas projeções existirem. A definição detalhada
dos 67 painéis, regras de cálculo, matrizes de fontes e permissões permanece:

- [Especificação funcional e arquitetura](dashboard-specification.md)
- [Catálogo dos 67 painéis](dashboard-panel-catalog.md)
- [Contratos de dados, definições e atribuição](dashboard-data-contracts.md)
- [Sequenciamento, critérios de aceite e testes](dashboard-implementation-plan.md)
- [Issue GitHub #8](https://github.com/jvteodoro/BookRush/issues/8)

O Overview existente não deve ser confundido com esses painéis; estados
`NOT_INSTRUMENTED`, `NO_DATA` e `UNTRUSTED` não são convertidos em zeros.
