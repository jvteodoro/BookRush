# ADR-009 — Realms separados e IaC incremental das aplicações

Status: Accepted  
Date: 2026-09-14

## Context

O realm `bookrush` atualmente contém o cliente público de ingestão, o cliente
de serviço do catálogo e a fixture local `operator`. O mesmo Compose também
executa Keycloak, Jenkins, Backstage, PostgreSQL, Redis, SeaweedFS, Traefik e
as aplicações. O arquivo de realm importado no startup só é usado quando o
volume está vazio; não é um reconciliador de uma instalação populada.

## Decision

`bookrush` permanece o realm do produto nesta onda para preservar o issuer e as
identidades existentes. Será criado `bookrush-platform` como realm separado para
engenharia/administração, com clients dedicados para Jenkins, Backstage e
automação administrativa. Não haverá broker, vínculo automático por email ou
promessa de sessão única entre os realms. A sessão SSO vale apenas entre
clientes do mesmo realm.

O `master` fica restrito à administração da instância. O arquivo JSON atual é
somente seed inicial; a configuração recorrente será reconciliada por
`keycloak-config-cli` em um job/container separado, com versão fixada, recursos
gerenciados explicitamente e sem exclusão implícita de usuários, clients ou
roles removidos do YAML. Até a validação do reconciliador, não haverá segundo
writer para os mesmos recursos.

Compose modular continua sendo a declaração do runtime. Configuração não
secreta é versionada em `infrastructure/`; segredos são injetados em runtime e
dados/volumes/backups ficam fora do Git. Terraform/OpenTofu permanece uma opção
futura para DNS, VM ou cloud; não será usado para controlar os mesmos containers
que Compose controla.

## Trust matrix atual

| Fronteira | Issuer | Audience/client | Permissão | Owner |
|---|---|---|---|---|
| Produto web | `bookrush` | `bookrush-web` (a provisionar) | APIs de produto | frontend/catalog |
| Ingestão administrativa | `bookrush-platform` (compatibilidade explícita pode ser habilitada por rota) | `bookrush-ingestion-admin` | OPERATOR/REVIEWER/CLEANUP por rota | ingestion |
| Catálogo interno | `bookrush-platform` | clients S2S separados; audience `bookrush-catalog-admin` | scope canônico ou CATALOG_SERVICE | catalog |
| Engenharia | `bookrush-platform` (a provisionar) | Jenkins/Backstage/admin clients | grants explícitos por aplicação | platform |
| Object storage | credenciais S3 | bucket/policy | capability assinada temporária | storage |

O estado final terá endpoints administrativos restritos ao issuer
`bookrush-platform` e audience correspondente. A janela de compatibilidade será
delimitada por rota e desligada explicitamente; JWT inválido nunca cai para
token estático. `(iss, sub)` continua sendo a identidade técnica, sem unir
contas por email.

## Migration and rollback

1. Fazer backup consistente e validar acesso break-glass fora do SSO.
2. Provisionar `bookrush-platform` e clients sem remover o realm `bookrush`.
3. Adicionar validadores por issuer/audience nas rotas administrativas.
4. Migrar operadores, S2S e ferramentas; revalidar replay/idempotência.
5. Retirar trust e tokens legados após métricas sanitizadas zerarem.

Rollback reverte configuração e clients sem apagar realms, usuários, migrations
ou volumes. Sessões e JWTs emitidos anteriormente podem permanecer válidos até
seus TTLs; isso será medido e documentado.

## Alternatives Considered

* Renomear `bookrush` para produto: altera issuer e subjects, exigindo migração
  de todas as identidades e auditorias.
* Usar um único realm para tudo: mistura usuários finais com administração.
* Usar Kubernetes/Terraform para containers locais: introduz controlador e
  dependência que não existem no host atual.

## Consequences

Há dois conjuntos de clients e políticas a operar, mas um usuário do produto
não ganha acesso ao Jenkins/Backstage por compartilhar username, email ou nome
de role. Rebuild e restore são operações distintas e nenhum clone do Git contém
identidades ou dados históricos.

## References

* [Inventário real do checkout](../platform/baseline-inventory.md)
* [Autenticação](../auth/README.md)
* [Contrato de lifecycle](../platform/lifecycle.md)
