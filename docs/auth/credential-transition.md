# Runbook de transição de credenciais

1. Provisionar o cliente Keycloak específico no realm `bookrush-platform`, com audience e scopes mínimos, sem remover clientes ou usuários atuais.
2. Configurar o serviço para validar issuer, audience, assinatura, `exp`/`nbf` e algoritmo permitido.
3. Habilitar a flag de compatibilidade somente no ambiente de transição e observar métricas agregadas de uso legado; não registrar tokens.
4. Reexecutar um job idempotente e confirmar que o principal técnico mapeado preserva a chave de idempotência e o fencing.
5. Migrar consumidores, desligar a flag e observar uma janela operacional.
6. Em rollback, reverter a configuração da aplicação e do cliente, sem apagar realm, usuários ou volumes; repetir a operação com a mesma chave.

## Estado de transição S2S

O ingestion-service usa dois clientes técnicos independentes quando
`INGESTION_OIDC_S2S_ENABLED=true`:

| Cliente | Uso | Scope | Endpoint protegido |
| --- | --- | --- | --- |
| `bookrush-canonical-service-s2s` | comandos canônicos e linhagem | `bookrush.catalog.write` | `/api/internal/v1/catalog/**` |
| `bookrush-asset-service-s2s` | criação de versões de assets | `bookrush.assets.write` | `/api/admin/**` |

Os secrets entram somente por ambiente protegido. A aplicação obtém tokens por
`client_credentials`, mantém cache apenas em memória e renova antes do `exp`.
Se nenhum modo estiver habilitado, a chamada falha fechada antes de executar o
comando; não há upload ou linhagem silenciosamente ignorados.
O modo estático legado é opt-in (`INGESTION_LEGACY_STATIC_TOKENS_ENABLED` e
`CATALOG_SECURITY_LEGACY_STATIC_ENABLED`) e deve permanecer desligado em
produção após a validação do fluxo OIDC. JWT inválido nunca cai para o token
estático. As chaves de idempotência e `operationKey` dos jobs não mudam com a
troca de principal; replay continua sendo decidido pelo job persistido e pelo
fence do catálogo.

O rollback não corrige migration, não executa Flyway repair e não transforma um JWT inválido em bearer estático. A retirada definitiva ocorre apenas após não haver uso legado observável e após validação de todas as rotas administrativas.

Após a reconciliação, os secrets dos clients técnicos devem ser obtidos uma
única vez por canal administrativo protegido e gravados no mecanismo de
segredos do host. Eles não devem ser colocados no JSON do realm, no `.env`
versionado ou em logs. O reconcile declara clients e scopes, mas não transforma
um secret exportado em backup; a recuperação precisa incluir o material externo
de segredos.
