# LabSoft Admin Web

Console administrativa em React/TypeScript para operar a plataforma de livros: importação de catálogo, execução de analytics e calibração inicial do algoritmo de feed com usuários administrativos do Keycloak.

## Quick start

```bash
cp .env.example .env
npm install
npm run dev
```

Por padrão `VITE_USE_MOCKS=true`, permitindo validar toda a UI sem backend.

## Produção

1. Configure um client público no realm administrativo do Keycloak.
2. Habilite Authorization Code + PKCE, sem client secret no browser.
3. Cadastre as URLs de redirect/origins da aplicação.
4. Defina `VITE_USE_MOCKS=false` e os URLs dos serviços.
5. Garanta que os microserviços validem o JWT diretamente contra o issuer/JWKS do realm. O frontend apenas transporta o bearer token; não deve existir um “endpoint para validar token” a cada request.

## Módulos

- **Overview**: estado resumido do corpus, analytics e training.
- **Catalog & Import**: seleção de obras candidatas e disparo da ingestão.
- **Analytics**: execução por livro e inspeção dos resultados/versionamento.
- **Feed Lab**: sessão de treino com scroll, sinais explícitos e tuning de parâmetros.

Veja `docs/ARCHITECTURE.md`, `docs/API-CONTRACTS.md` e `docs/KEYCLOAK.md`.
