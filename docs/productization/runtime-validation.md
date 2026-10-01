# Validação runtime descartável

Os testes de runtime não usam containers, volumes ou a rede do ambiente
BookRush em execução. `scripts/test-compose.sh` atribui nome de projeto, rede
e portas exclusivos, usa credenciais PostgreSQL descartáveis e injeta valores
fictícios somente para interpolar o perfil opcional SeaweedFS OIDC. Ao
terminar, remove os recursos criados pelo teste.

```bash
bash scripts/test-database.sh
bash scripts/test-compose.sh
bash scripts/test-keycloak-realms.sh
bash scripts/test-storage.sh
```

Em 2026-10-01, o banco passou com 43 testes de integração e zero falhas,
incluindo banco vazio, upgrade de schemas e migration V15. O Compose completo
subiu PostgreSQL, Redis, SeaweedFS, catálogo, ingestão, analytics, serviços de
produto, admin, frontend e proxy, verificando `/api/status`. O teste de
Keycloak confirmou descoberta OIDC dos dois realms declarativos. O teste de
storage confirmou URL assinada após recriação do SeaweedFS e restore lógico do
PostgreSQL/restore físico do volume.

As falhas encontradas durante a validação foram corrigidas no mesmo conjunto:
contexto Docker incorreto no teste de banco, rede fixa que podia alcançar
PostgreSQL externo, YAML com chaves duplicadas ou fora do namespace e
interpolação de secrets opcionais ausentes. Nenhum segredo real foi registrado.
