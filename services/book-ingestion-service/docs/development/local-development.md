# Desenvolvimento local

Na raiz do monorepo:

```bash
cp .env.example .env
# Preencha as credenciais de desenvolvimento conforme README raiz.
docker compose -f infrastructure/compose.yaml --env-file .env --profile auth up -d --build
```

O serviço book-ingestion-service usa rede Docker; não requer Maven instalado no host.
