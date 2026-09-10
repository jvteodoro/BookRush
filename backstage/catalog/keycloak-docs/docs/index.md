# Keycloak

Keycloak fornece a identidade OIDC para os microserviços e para o Developer
Portal. O realm versionado está em
[`infrastructure/keycloak/bookrush-realm.json`](https://github.com/jvteodoro/BookRush/blob/main/infrastructure/keycloak/bookrush-realm.json).

A configuração pública do cliente não é uma credencial. Secrets e ajustes de
produção são fornecidos por variáveis de ambiente e pelo ambiente operacional.
