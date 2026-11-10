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

### Estatísticas de publicador

A rota `/publisher` (publicada em `/admin/publisher`) mostra somente os livros da conta de publicação autenticada no realm de plataforma. Selecione uma publicação e o período de 7, 30, 90 ou 365 dias para consultar totais e tabela diária. Ausência de publicação confirmada, falta de atividade e falha de consulta têm estados distintos. A consulta sempre usa o HTTP client autenticado, sem dados de demonstração. O publisher-service precisa da identidade S2S descrita em `services/publisher-service/docs/api.md`.

Teste do fluxo no Chromium: `node --test tests/publisher-statistics.e2e.mjs` (CHROMIUM_PATH pode selecionar um executável instalado). O teste usa API simulada e confirma bearer, troca de livro/período, totais, ausência de atividade e recuperação de erro; não substitui login e smoke no gateway real.
