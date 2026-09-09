# Segurança do serviço de ingestão

Em produção, `book-ingestion-service` usa OAuth2 Resource Server com issuer,
audience e JWKS configuráveis. O validador verifica assinatura, issuer,
audience e expiração; a lista de chaves pode rotacionar sem alterar o código.
`INGESTION_SECURITY_ENABLED` deve ser `true` fora do perfil local.

Os papéis são separados:

- `READER`: consulta somente;
- `OPERATOR`: cria, cancela e retoma jobs;
- `REVIEWER`: decide matches e direitos;
- `CLEANUP`: executa limpeza/reconciliação destrutiva após auditoria.

O token estático `ASSET_ADMIN` permanece restrito ao catálogo legado e não é
aceito como autorização universal pelo serviço de ingestão. Credenciais
service-to-service do `CanonicalCatalogPort` terão escopo próprio.

O modo `local-fixture-mode` é explícito e só deve liberar identidades do
harness offline. Ele não desliga validação de issuer/audience em produção, não
permite URLs arbitrárias e não deve ser ativado no Compose produtivo.
