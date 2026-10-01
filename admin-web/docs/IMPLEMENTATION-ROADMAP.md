# Implementation roadmap

## Phase 1 — integration foundation
- Criar client Keycloak da SPA.
- Habilitar JWT Resource Server nos serviços.
- Substituir adapters mock por adapters HTTP reais.
- Adicionar tratamento padronizado de Problem Details / RFC 9457.

## Phase 2 — catalog operations
- Busca paginada.
- Tela de detalhes do livro.
- Jobs assíncronos de importação com retry explícito.
- Provenance/licença/origem do conteúdo.

## Phase 3 — analytics operations
- Pipelines selecionáveis.
- Histórico de versões.
- Comparação entre runs.
- Visualização de feature distributions e candidatos de excerpt.

## Phase 4 — feed training
- Infinite scroll real.
- Impression e dwell time confiáveis com Page Visibility API.
- Score decomposition.
- Session snapshots.
- Comparador A/B offline.
- Promoção controlada de parameterVersion.

## Phase 5 — governance
- Audit log.
- RBAC por role.
- Export de experimentos.
- Separação ADMIN_SEED vs END_USER.
- Retenção e anonimização dos eventos.
