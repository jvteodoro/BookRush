# ADR-008 — Transição de credenciais estáticas para clientes OIDC

Status: Accepted  
Date: 2026-09-14

## Context

As integrações internas ainda possuem `CANONICAL_SERVICE_TOKEN`, `INGESTION_ASSET_TOKEN` e, em alguns caminhos, `X-Canonical-Service-Token`. Esses segredos não distinguem adequadamente usuários, clientes e operações.

## Decision

Cada integração interna migrará para client credentials do Keycloak, com cliente, audience e scopes separados por serviço. O usuário do produto, o Backstage e outros clientes nunca receberão os scopes de catálogo canônico ou administração de assets. A transição será controlada por flag explícita, desligada no estado final de produção, com métricas sanitizadas e janela de rollback.

Um JWT inválido nunca fará fallback para token estático. Durante a janela de compatibilidade, o token legado só será aceito quando a flag de transição estiver explicitamente habilitada e apenas na rota/operação documentada. A identidade usada em jobs e chaves de idempotência será mapeada de modo estável para o principal técnico OIDC; replay deve continuar produzindo o mesmo resultado.

## Alternatives Considered

* Manter um bearer global é simples, mas não permite revogar uma integração sem afetar as demais.
* Usar o cliente do frontend para S2S mistura usuários e automação.
* Criar um auth-service duplica o Keycloak e acrescenta outro emissor.

## Consequences

Há mais clientes e segredos operacionais, porém cada permissão pode ser reduzida e auditada. O rollout exige provisionamento idempotente, testes de replay e observação de chamadas legadas antes da retirada.

## Risks

Um audience ou scope incorreto pode bloquear workers legítimos. O rollout deve provar o cliente correto em ambiente descartável e manter procedimento de rollback sem apagar usuários ou volumes.

## References

* [Clientes, papéis e scopes](../auth/README.md)
* [Runbook de transição](../auth/credential-transition.md)
