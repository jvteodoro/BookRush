# Segurança

A aplicação usa Authorization Code + PKCE S256 com o realm
`bookrush-platform` e o client público `labsoft-admin-web`. Nenhum client
secret é aceito no bundle. O access token permanece em memória e é anexado
como `Authorization: Bearer`; o `sub` do JWT é a identidade canônica.

Em produção, o callback permitido é `https://bookrush.jteodoro.tec.br/admin/*`
e a origem é `https://bookrush.jteodoro.tec.br`. O client está declarado em
`infrastructure/keycloak/bookrush-platform-realm.json` e deve ser reconciliado
com `bash infrastructure/keycloak/reconcile.sh --env-file .env` quando o realm
já estiver populado; o import de startup não altera um realm existente.

Roles controlam apenas a experiência visual. A autorização real permanece nos
serviços. `catalog_operator` opera importações, `analytics_operator` dispara
analytics, `feed_trainer` usa o laboratório e `feed_admin` pode promover uma
versão validada. O backend deve rejeitar tokens de produto ou de outro issuer.

Eventos não aceitam `userId` do browser como fonte de verdade. O adapter envia
`eventId` e idempotência; o backend deriva o principal do JWT e grava
`population=ADMIN_SEED` na sessão.
