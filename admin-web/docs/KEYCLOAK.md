# Keycloak integration

## Client recomendado

- Realm: o realm administrativo já existente.
- Client ID: `labsoft-admin-web`.
- Client type: public.
- Standard flow: ON.
- PKCE: S256.
- Direct access grants: OFF.
- Service accounts: OFF para o client da SPA.
- Lightweight access tokens: OFF. As APIs precisam receber `sub` no access
  token para formar a identidade técnica `(iss, sub)`; o ID token não deve ser
  reutilizado como bearer da API. Se uma emissão compatível ainda omitir `sub`,
  o ingestion-service usa somente o `sid` opaco da sessão como fallback
  limitado; email, username e grupos nunca são usados como identidade.
- Valid redirect URIs: URLs exatas do frontend.
- Post logout redirect URI: `https://bookrush.jteodoro.tec.br/*`; ela precisa
  estar cadastrada no atributo `post.logout.redirect.uris` do client, além da
  URI de login. Sem essa configuração, o Keycloak exibe `Invalid redirect uri`
  ao sair da SPA.
- Web origins: origem do frontend.

## Roles sugeridas

- `admin`: acesso geral à console.
- `catalog_operator`: importar/reprocessar livros.
- `analytics_operator`: disparar pipelines.
- `feed_trainer`: iniciar sessões e enviar sinais.
- `feed_admin`: promover uma parameterVersion para baseline.

A UI pode esconder ações conforme role, mas a autorização real deve estar no backend.

## Backend Spring Security

Cada microserviço deve operar como OAuth2 Resource Server e validar JWT localmente pelo issuer/JWKS do Keycloak. Exemplo conceitual:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example/realms/admin
```

Mapear `realm_access.roles` para authorities. Evitar um microserviço central de “token validation” no caminho crítico: isso adiciona latência e acoplamento desnecessários.
