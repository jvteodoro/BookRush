# Autenticação e autorização BookRush

O Keycloak é o provedor central de identidade. O realm `bookrush` é a fronteira
do produto e `bookrush-platform` é a fronteira de engenharia/administração.
Provisionamento deve ser incremental e idempotente, sem importar um realm vazio
sobre uma instalação populada.

## Fluxo do usuário

O frontend é uma SPA e adotará Authorization Code + PKCE S256 com um cliente público dedicado. Tokens permanecem em memória. A biblioteca OIDC verifica `state`, `nonce`, PKCE, issuer e expiração; redirects são allowlists por ambiente. O fluxo é:

```text
browser → Keycloak /authorize (PKCE)
        ← callback local com code/state
browser → Keycloak /token
browser → APIs BookRush com Authorization: Bearer
browser → Keycloak /logout ao sair
```

Não há password grant, banco de senhas, JWT próprio, token em storage do navegador ou segredo no bundle. Se o IdP estiver indisponível, a tela informa falha recuperável sem loop. 401 pede autenticação; 403 indica ausência de permissão.

O frontend usa `oidc-client-ts` com `userStore` em memória. Apenas o estado
transitório necessário para completar o callback OIDC fica no `stateStore` da
biblioteca e é descartado após o callback; access/refresh tokens não são
persistidos em localStorage ou sessionStorage.

Para desenvolvimento, `VITE_OIDC_AUTHORITY` e `VITE_OIDC_CLIENT_ID` são
configurações públicas opcionais do Vite. O padrão aponta para o realm de
produto e `bookrush-web`; não coloque client secret nessas variáveis.

## Identidades, clientes, papéis e scopes

| Ator | Cliente/audience | Uso | Permissões |
|---|---|---|---|
| Usuário do produto | `bookrush-web` | consultas da aplicação | somente APIs explicitamente públicas/protegidas do produto |
| Operador | `bookrush-platform` / `bookrush-ingestion-admin` | jobs de ingestão | `OPERATOR`, `REVIEWER`, `CLEANUP` conforme rota |
| Serviço canônico | `bookrush-canonical-service-s2s` | integração ingestão → catálogo | audience `bookrush-catalog-admin`, scope `bookrush.catalog.write` |
| Administração de assets | usuário administrativo ou `bookrush-asset-service-s2s` | aprovação/limpeza/distribuição | `ASSET_ADMIN` ou `bookrush.assets.write` conforme rota |
| Backstage | `bookrush-platform` / cliente separado | catálogo/TechDocs | não herda permissões operacionais |

`(iss, sub)` é a identidade estável; `sub` é string opaca, não UUID nem email. READER operacional não significa leitor do produto. A matriz detalhada de rotas permanece nos serviços e deve acompanhar cada alteração de endpoint.

## APIs e fronteiras

Endpoints públicos atuais, como status e documentação, continuam públicos. As rotas administrativas de ingestão exigem roles operacionais do realm administrativo; catálogo interno exige issuer/audience e scope/principal técnico corretos; administração de assets exige `ASSET_ADMIN` humano ou o scope técnico explicitamente delimitado. Ausência ou token inválido retorna 401; token válido sem role ou scope retorna 403. Headers de identidade enviados pelo cliente não são aceitos como autenticação.

O starter `bookrush-security-spring-boot-starter` é opt-in (`bookrush.security.enabled=true`) e cria somente `JwtDecoder` e converter de identidade quando o serviço não fornece seus próprios beans. Ele não registra `SecurityFilterChain`, não libera rotas e não aceita issuer/JWKS derivados do token. Cada serviço configura issuer, JWKS e audience exatos por fronteira; um serviço que misture realms deve usar chains/resolvers por rota com allowlist explícita.

### Falha de inicialização por conversor JWT

O conversor fornecido pelo starter precisa permanecer como uma classe concreta com
parâmetros genéricos (`Converter<Jwt, AbstractAuthenticationToken>`). Uma lambda
registrada como bean também pode ser descoberta pelo Spring MVC como um conversor de
formatação; como a lambda não expõe os tipos fonte/destino por reflexão, o serviço
falha durante a criação do `mvcConversionService` com `Unable to determine source
type`. Depois de alterar o starter, reconstrua todas as imagens que o consomem, não
apenas o catálogo, e valide o healthcheck de cada serviço.

## Sessão, TTL e logout

O TTL efetivo é o configurado no realm e nos clientes Keycloak; ele deve ser registrado no ambiente, não hardcoded no frontend. Refresh é serializado para evitar chamadas concorrentes. Logout limpa memória e encerra a sessão OIDC. Tokens emitidos anteriormente podem permanecer válidos até `exp`.

O cliente web renova somente quando o access token está próximo de expirar e compartilha uma única promessa de refresh. Requisições são same-origin e allowlisted em `/api/`; 401 remove o usuário em memória e 403 não dispara novo login automaticamente.

## Clientes de serviço e migração

`CANONICAL_SERVICE_TOKEN`, `INGESTION_ASSET_TOKEN` e `X-Canonical-Service-Token` são legado de transição, nunca fallback de JWT inválido. A migração usa clientes distintos, audience e scopes mínimos. A flag legada fica desligada no estado final de produção. O procedimento está em [credential-transition.md](credential-transition.md); a matriz de origem/destino e rollback está em [realm-migration.md](realm-migration.md).

## Configuração como código

O login administrativo do Jenkins e o diagnóstico do callback estão descritos
em [OIDC do Jenkins com Keycloak](jenkins-oidc.md).
O login do Developer Portal segue o mesmo realm administrativo, com client
separado e configuração em `backstage/app-config.oidc.yaml`; os detalhes
operacionais estão em `backstage/docs/security.md`.

Os dois realms declarativos podem ser verificados sem usuários ou volumes
persistentes com:

```bash
bash scripts/test-keycloak-realms.sh
```

Esse ensaio confirma a importação de `bookrush` e `bookrush-platform` e a
descoberta OIDC. Ele não cria credenciais humanas nem substitui a prova E2E de
login, MFA e autorização através do gateway.

O produto permanece no realm `bookrush`; engenharia/administração está isolada
no realm `bookrush-platform`. O segundo realm não cria SSO entre fronteiras: a
sessão do IdP só é reutilizada entre clientes do mesmo realm. O `master` é
somente administrativo.

Os arquivos `infrastructure/keycloak/bookrush-realm.json` e
`bookrush-platform-realm.json` são configuração declarativa e ambos são
montados no import de um volume vazio. O import de startup
(`--import-realm`) serve apenas para bootstrap de volume vazio. Em instalação
populada, execute `bash infrastructure/keycloak/reconcile.sh --env-file .env`;
ele usa `adorsys/keycloak-config-cli:6.3.0-18.0.2` em um serviço Compose de
profile explícito. A credencial de reconcile deve ser protegida e diferente do
uso diário do bootstrap admin quando a instalação permitir.

## Desenvolvimento e operação

O cliente local deve usar redirect URI explícita para a porta local e credenciais fornecidas em runtime. Nunca copie `.env` para Git. O frontend usa o issuer público `https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush`; administração e S2S usam `https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush-platform`. JWKS fica sob configuração confiável; rotação de `kid` não exige rebuild.

Referências: [ADR-007](../adr/ADR-007-product-login-pkce.md), [ADR-008](../adr/ADR-008-service-credential-transition.md) e [documentação de política](../documentation-policy.md).
