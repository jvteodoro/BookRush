# Matriz de migração de realms

Esta é a matriz declarativa para a transição. Ela não migra subjects nem
executa alteração em instalação persistente por si só.

| Origem atual | Destino | Tipo | Issuer/audience | Ação e risco |
| --- | --- | --- | --- | --- |
| `bookrush-web` ausente | `bookrush/bookrush-web` | SPA público | issuer produto, audience `bookrush-web` | Authorization Code + PKCE; redirect HTTPS explícito |
| `bookrush-ingestion` | `bookrush-platform/bookrush-ingestion-admin` | operador humano | issuer plataforma, audience `bookrush-ingestion` | reenrolar operadores/MFA; não unir por email |
| `bookrush-catalog-service` | `bookrush-platform/bookrush-canonical-service-s2s` | S2S canônico | audience `bookrush-catalog-admin`, scope `bookrush.catalog.write` | segredo novo e replay com mesma operationKey |
| token `INGESTION_ASSET_TOKEN` | `bookrush-platform/bookrush-asset-service-s2s` | S2S asset | scope `bookrush.assets.write` | retirar bearer estático após janela medida |
| Jenkins/Backstage | clients dedicados no realm plataforma | ferramenta humana | issuer plataforma | cada aplicação mapeia seus próprios grants |
| `operator` fixture | sem migração automática | dado de desenvolvimento | realm produto | não autoriza administração; remover em ambiente real |

O realm `master` não aparece como destino de aplicações. O campo `(iss, sub)`
é a chave técnica de auditoria; subjects novos são identidades novas mesmo que
o email coincida. Jobs antigos conservam seu principal e idempotency key até a
reconciliação explícita. A compatibilidade por issuer é permitida somente nas
rotas e no período registrados no runbook de transição, nunca como fallback
global.

## Ordem e rollback

1. Backup consistente de Keycloak, banco e objetos; confirmar break-glass.
2. Criar o realm plataforma e clients sem remover o realm produto.
3. Implantar validadores e clients técnicos; testar replay e fencing.
4. Migrar operadores, Jenkins e Backstage; exigir MFA para grupos privilegiados.
5. Migrar frontend do produto e retirar trust/segredos legados após métricas
   agregadas zerarem.

Rollback altera configuração e clients, sem apagar realms, usuários, migrations
ou volumes. Sessões não são prometidas como recuperáveis; JWTs já emitidos
expiram conforme o TTL configurado.
