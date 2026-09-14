# Inventário de interfaces e fronteiras de acesso

Este inventário foi conferido contra `infrastructure/compose.yaml`, o provider
dinâmico do Traefik e os exemplos de virtual hosts Nginx. Ele evita instalar
uma ferramenta somente para criar uma integração SSO.

| Interface | Exposição atual | Identidade humana | Identidade de máquina | Decisão |
| --- | --- | --- | --- | --- |
| Frontend BookRush | Nginx → Traefik → `frontend`, HTTPS público | OIDC `bookrush-web` no realm `bookrush` | chamadas same-origin com access token em memória | SSO de produto |
| APIs catálogo/ingestão/analytics | Prefixos `/api`, `/ingestion`, `/analytics`; serviços sem portas públicas | Bearer OIDC nas rotas protegidas | client credentials ou credenciais legadas opt-in | autorização no serviço |
| Keycloak | host público dedicado via proxy | administração no `master`/realm administrativo conforme operação | bootstrap e config-cli separados | não usar `master` como realm de aplicação |
| Jenkins | porta loopback, virtual host Nginx | cliente OIDC do realm `bookrush-platform` ainda requer plugin/JCasC validado | agents, webhooks e credenciais Jenkins | não converter credenciais de jobs em OIDC |
| Backstage | porta loopback, `docs-bookrush...` | cliente OIDC administrativo conforme configuração existente | catálogo/TechDocs usam suas próprias credenciais | não herda permissões de ingestão |
| pgAdmin | loopback (`18083`) | login local da aplicação | conexão PostgreSQL configurada no ambiente | sem exposição pública ou proxy de identidade nesta onda |
| SeaweedFS/S3 | endpoint interno; console/porta não publicados pelo Compose | nenhuma sessão SSO | access/secret key e URLs assinadas | buckets permanecem privados |
| PostgreSQL/Redis | rede Compose, sem porta pública declarada para uso da aplicação | nenhuma | credenciais de serviço | não publicar consoles |

Jenkins, Backstage, Keycloak e pgAdmin têm interfaces web, mas somente Jenkins,
Backstage e frontend têm clientes administrativos/product previstos nesta onda.
SeaweedFS, S3, PostgreSQL, Redis e CLI continuam protocolos de máquina; uma URL
assinada é uma capability temporária, não uma sessão humana. Nenhuma rota do
produto autoriza acesso administrativo por semelhança de email, username ou
role emitida pelo realm errado.

## Validação e limitações

Executado localmente: `awk` para inventário do Compose, inspeção do provider
Traefik, validação JSON dos dois realms e `git diff --check`. Não executado:
login real no Jenkins/Backstage, porque o daemon Docker/IdP não está disponível
nesta sessão. A integração desses clientes só deve ser ativada após confirmar
as propriedades do plugin e do JCasC instalados; não há proxy genérico que
converta autenticação em autorização.
