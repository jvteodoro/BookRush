# Architecture

## Objetivo

A Admin Web é uma SPA React que serve como plano de controle para três capacidades do backend: conteúdo, analytics e recomendação. Ela não contém regra de negócio definitiva; toda operação mutável é enviada aos microserviços e auditada no backend.

## Fronteiras

### Identity
Keycloak é o IdP. A aplicação usa Authorization Code Flow + PKCE com `keycloak-js`. O access token identifica o usuário administrativo. O `sub` do JWT é o identificador canônico do usuário para eventos de treino.

### Catalog / Import
Responsável por descoberta de obras, licença, estado da ingestão e disparo de importação. A UI assume que a ingestão pode ser assíncrona; em produção, o POST deve retornar um job/idempotency key e a UI deve acompanhar status.

### Analytics
Executa pipelines versionados por livro/corpus. Resultados devem guardar `modelVersion`, `datasetVersion`, timestamps, status, métricas, features produzidas e provenance.

### Feed Lab
O laboratório transforma administradores autenticados em usuários iniciais de calibração. Cada sessão congela uma configuração inicial e cria versões quando pesos mudam. Cada evento contém `userId`, `sessionId`, `excerptId`, `parameterVersion` e timestamp.

## Regra importante de experimentação

Nunca sobrescrever o “peso atual” global como efeito colateral de sliders. O ajuste no laboratório cria uma nova `parameterVersion` da sessão. Promoção para baseline global deve ser uma operação distinta, autorizada e auditada.

## Separação de dados

Eventos de administradores precisam carregar `population=ADMIN_SEED` no backend para que métricas futuras consigam separar comportamento de bootstrap de comportamento real dos usuários finais. Não misturar silenciosamente essas populações no treinamento.

## Fluxo esperado

1. Administrador autentica via Keycloak.
2. Seleciona livro em Catalog.
3. Backend importa texto/metadata e marca obra como pronta.
4. Admin dispara analytics.
5. Analytics gera features e candidatos a trechos.
6. Feed service ranqueia candidatos.
7. Feed Lab cria training session associada ao `sub` do Keycloak.
8. Impressões, dwell time, skip, like, dislike, save e open-book viram eventos.
9. Parâmetros podem mudar no escopo da sessão, sempre versionados.
10. Configuração validada pode depois ser promovida para baseline por endpoint dedicado.
