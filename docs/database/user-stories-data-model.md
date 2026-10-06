# Projeto de dados para as histórias de usuário

## Escopo e método

Este documento traduz as histórias de usuário de **“Aula 5 - Definição dos Requisitos Funcionais”** em um projeto de dados para o BookRush. A análise foi feita em 2026-10-06 contra as migrations versionadas e o PostgreSQL em execução.

O Keycloak continua sendo a autoridade de autenticação, credenciais e papéis. PostgreSQL guarda somente estado de produto, auditoria e projeções. O banco usa schemas por bounded context: um serviço não escreve tabelas de outro; referências entre contexts são IDs contratuais, validados pelo serviço dono ou por eventos/projeções. FKs ficam no contexto que detém ambos os lados.

### Chaves e convenções

- `book_id`, `edition_id`, `asset_id`, `chapter_id` e IDs de workflow são UUIDs canônicos do `catalog`.
- `subject_key` é a identidade técnica opaca derivada de `(issuer, subject)`; não é e-mail e nunca é informado pelo cliente como ator.
- Datas usam `TIMESTAMPTZ`; métricas diárias usam data UTC explicitamente.
- Decisões administrativas, direitos e publicação precisam de ator, instante, motivo e estado. Ações não sobrescrevem o histórico.
- Texto integral e binários ficam no object storage; o banco armazena metadata, versões, offsets, hashes e referências.

## Estado já entregue

| Domínio | Schema/tabelas existentes | Cobertura atual |
|---|---|---|
| Catálogo e busca | `catalog.book`, `edition`, `author`, créditos, `subject`, `book_subject`, assets, versões, direitos e capítulos | Obras, autores, assuntos, busca por título/autor/palavra-chave e conteúdo reader-ready têm base relacional. |
| Leitura | `catalog.book_chapter`; `reader_state.progress`, `bookmarks`, `recent`, `session`, `reading_day`, `library` | Abrir livro, exibir capítulos, marcar posição, salvar livros, recentes e streak possuem persistência. |
| Perfil e social | `reader_profile.profile`; `social.follows`, `likes`, `comments`, `shares`, `reports` | Perfil público simples, seguir, curtir, comentar, compartilhar e denunciar estão persistidos. |
| Recomendações | `recommendation.request`, `impression`; `behavior.events`, agregados diários | Há ledger de entrega/impressão e fatos de uso. O algoritmo atual é `heuristic-v1`, sem personalização real. |
| Administração | `admin.audit_log`, `admin.moderation`; relatórios em `social.reports` | Painel pode listar usuários/profiles, denúncias, decisões registradas e auditoria. |
| Publicador | `publisher.submission`, `staged_upload` e vínculo de ingestão/catálogo | Rascunho, upload em staging, submissão e associação posterior a livro canônico existem. |
| Analytics de trechos | `analytics.excerpt`, ranks, embeddings, features e elegibilidade | Há base para selecionar trechos; integração completa ao feed ainda é evolutiva. |

### Diagrama E/R — núcleo implementado

O diagrama omite tabelas de Flyway, auditoria de ingestão e features analíticas; elas estão detalhadas em [schema.md](schema.md).

```mermaid
erDiagram
    BOOK ||--o{ EDITION : possui
    BOOK ||--o{ BOOK_AUTHOR : credita
    AUTHOR ||--o{ BOOK_AUTHOR : participa
    BOOK ||--o{ BOOK_SUBJECT : classifica
    SUBJECT ||--o{ BOOK_SUBJECT : categoriza
    BOOK ||--o{ BOOK_ASSET : possui
    EDITION o|--o{ BOOK_ASSET : contextualiza
    BOOK_ASSET ||--o{ BOOK_ASSET_VERSION : versiona
    BOOK ||--o{ BOOK_CHAPTER : projeta
    BOOK_ASSET_VERSION ||--o{ BOOK_CHAPTER : texto_base

    READER_PROFILE ||--o{ READER_LIBRARY : salva
    READER_PROFILE ||--o{ READER_PROGRESS : le
    READER_PROFILE ||--o{ READER_BOOKMARK : marca
    READER_PROFILE ||--o{ READER_RECENT : abre
    READER_PROFILE ||--o{ READING_SESSION : inicia
    BOOK ||--o{ READER_LIBRARY : referencia
    BOOK ||--o{ READER_PROGRESS : referencia
    BOOK ||--o{ READER_BOOKMARK : referencia

    READER_PROFILE ||--o{ SOCIAL_FOLLOW : segue
    READER_PROFILE ||--o{ SOCIAL_LIKE : curte
    READER_PROFILE ||--o{ SOCIAL_COMMENT : comenta
    BOOK ||--o{ SOCIAL_LIKE : recebe
    BOOK ||--o{ SOCIAL_COMMENT : recebe
    BOOK ||--o{ SOCIAL_SHARE : compartilha
    SOCIAL_COMMENT o|--o{ SOCIAL_REPORT : denunciado
    BOOK o|--o{ SOCIAL_REPORT : denunciado

    READER_PROFILE ||--o{ RECOMMENDATION_REQUEST : recebe
    RECOMMENDATION_REQUEST ||--o{ RECOMMENDATION_IMPRESSION : entrega
    BOOK ||--o{ RECOMMENDATION_IMPRESSION : recomendado

    READER_PROFILE ||--o{ PUBLISHER_SUBMISSION : envia
    PUBLISHER_SUBMISSION ||--o{ PUBLISHER_STAGED_UPLOAD : anexa
    PUBLISHER_SUBMISSION o|--o| BOOK : canoniza_em
```

`READER_PROFILE` é a identidade lógica `subject_key`; perfil, social e leitura pertencem, respectivamente, aos schemas `reader_profile`, `social` e `reader_state`.

## Matriz de histórias e lacunas

| Ator e história | Estado | Evidência existente | Complemento necessário no banco |
|---|---|---|---|
| Leitor — recomendações | Parcial | request/impression, eventos e agregados | Registrar estratégia/candidatos e operar ranking que use sinais por leitor. |
| Leitor — rolagem de trechos | Parcial | excerpts/ranks analytics e telemetria | Persistir entrega de trecho por superfície e política de elegibilidade. |
| Leitor — abrir e ler livro | Pronto na base | assets, versões, capítulos, progresso | Validar conteúdo/direito; opcionalmente persistir locator por capítulo/versão. |
| Leitor — busca | Pronto na base | `book`, `author`, `subject`, índices V15 e query multi-critério | Medir consultas e evoluir para índice textual multilíngue se necessário. |
| Leitor — perfil/atividades de terceiros | Parcial | perfil público e relações sociais | Política granular e feed de atividade visível. |
| Leitor — seguir, curtir, compartilhar, comentar | Parcial | follows/likes/shares/comments | Status de conteúdo e atividade pública; contadores como projeções. |
| Leitor — privacidade | Parcial | `profile.is_public` | Visibilidade por perfil, atividade, biblioteca, recentes e seguidores. |
| Leitor — marcação de página | Pronto na base | bookmarks e progress por codepoint | Guardar capítulo/versão/locator para sobreviver a nova versão textual. |
| Leitor — estou com sorte | Parcial | catálogo e ledger de recommendation | Estratégia `RANDOM`, seed/auditoria e filtros de elegibilidade. |
| Leitor — biblioteca | Pronto na base | `reader_state.library` | Estados opcionais e ordenação manual são evolução de produto. |
| Leitor — recentes | Pronto na base | `reader_state.recent` | Limite/retenção e exclusão pelo leitor são evolução. |
| Leitor — planta/streak | Parcial | sessões e `reading_day` | Definir fórmula/versionamento do estágio visual e eventos de conclusão. |
| Admin — administração e dashboards | Parcial | auditoria, moderação, reports e agregados | Projeções de KPI, filtros/paginação e retenção. |
| Admin — gerenciar usuários/bloquear | Parcial | lista de profiles/auditoria | Restrição local auditável e sincronização da suspensão real no Keycloak. |
| Admin — moderar denunciados | Parcial | reports e moderation livre | Caso tipado, estados de resolução e efeito material sobre comentário/publicação. |
| Publicador — publicar livro | Parcial | submission, upload e ingestão | Metadados, contributors, direitos, revisão e lifecycle de publicação. |
| Publicador — estatísticas | Parcial | daily_book e ligação parcial submissão–livro | Titularidade de publicação e métricas de share/comment/read por período. |

## Modelo alvo a acrescentar

As estruturas abaixo são o **desenho proposto**, não migrations aplicadas. Devem ser entregues em migrations novas, sem editar versões Flyway já aplicadas.

### Perfil, privacidade e atividade social

`profile.is_public` permanece compatível durante a migração; a fonte de decisão passa a ser uma política granular. Não se copia e-mail, senha ou papéis do Keycloak.

```mermaid
erDiagram
    PROFILE ||--|| PROFILE_PRIVACY : configura
    PROFILE ||--o{ PROFILE_BLOCK : bloqueia_localmente
    PROFILE ||--o{ SOCIAL_ACTIVITY : pratica
    PROFILE ||--o{ SOCIAL_FOLLOW : follower
    PROFILE ||--o{ SOCIAL_FOLLOW : followed
    SOCIAL_COMMENT o|--o{ SOCIAL_ACTIVITY : gera
    SOCIAL_LIKE o|--o{ SOCIAL_ACTIVITY : gera
    SOCIAL_SHARE o|--o{ SOCIAL_ACTIVITY : gera
    BOOK o|--o{ SOCIAL_ACTIVITY : alvo

    PROFILE_PRIVACY {
      text subject_key PK
      text profile_visibility
      text activity_visibility
      text library_visibility
      text recent_visibility
      text social_graph_visibility
    }
    PROFILE_BLOCK {
      uuid id PK
      text target_subject_key
      text status
      text reason_code
      text actor_subject_key
      timestamptz expires_at
    }
    SOCIAL_ACTIVITY {
      uuid id PK
      text actor_subject_key
      text activity_type
      uuid book_id
      uuid comment_id
      text visibility_snapshot
      timestamptz occurred_at
    }
```

- Visibilidades aceitam `PUBLIC`, `FOLLOWERS`, `PRIVATE`, com `CHECK`.
- `social.activity` é append-only e guarda referências, nunca o corpo do comentário. A API reavalia bloqueio e moderação ao ler.
- `profile_block` pode ser dono do `reader_profile` ou `admin` (decisão a fixar antes da migration). A suspensão de login é um comando ao Keycloak, auditado com confirmação/falha; o banco não deve alegar que a conta foi bloqueada no IdP sem confirmação.

### Leitura, recomendação e feed de trechos

`progress` e `bookmarks` funcionam para o texto atual. Para continuidade robusta entre versões, o locator precisa referenciar versão e capítulo. A recomendação deve registrar o que entregou para permitir explicação, repetição e avaliação de vieses.

```mermaid
erDiagram
    BOOK_ASSET_VERSION ||--o{ READER_PROGRESS : ancora
    BOOK_CHAPTER o|--o{ READER_PROGRESS : localiza
    BOOK_ASSET_VERSION ||--o{ READER_BOOKMARK : ancora
    BOOK_CHAPTER o|--o{ READER_BOOKMARK : localiza
    RECOMMENDATION_REQUEST ||--o{ RECOMMENDATION_ITEM : contem
    RECOMMENDATION_ITEM ||--|| RECOMMENDATION_IMPRESSION : mede
    BOOK ||--o{ RECOMMENDATION_ITEM : recomenda
    ANALYTICS_EXCERPT o|--o{ RECOMMENDATION_ITEM : apresenta

    READER_PROGRESS {
      text subject_key PK
      uuid book_id PK
      uuid text_asset_version_id
      uuid chapter_id
      jsonb locator
      numeric percent
    }
    RECOMMENDATION_REQUEST {
      uuid id PK
      text subject_key
      text strategy
      text model_version
      bigint random_seed
      text candidate_set_hash
    }
    RECOMMENDATION_ITEM {
      uuid id PK
      uuid request_id
      uuid book_id
      uuid excerpt_id
      int rank
      numeric score
      jsonb reason
    }
```

- Acrescentar `text_asset_version_id`, `chapter_id` e `locator JSONB` a progresso/bookmarks; manter `position_codepoint` durante migração.
- Evoluir `recommendation.request` com `strategy` (`PERSONALIZED`, `RANDOM`, `TRENDING`, `FEED`), `random_seed` e `candidate_set_hash`; criar `recommendation.item` e fazer `impression` referenciá-lo.
- “Estou com sorte” é request `RANDOM`: seed e filtros de direito, conteúdo reader-ready, idioma e bloqueio tornam o resultado auditável sem retirar aleatoriedade.
- `analytics.excerpt` continua dono do trecho/qualidade; `recommendation` é dono da seleção e entrega. O texto do excerpt não é duplicado.

### Administração e moderação

Hoje `social.reports` e `admin.moderation.target` são livres. Para ocultar comentário ou impedir publicação, a decisão precisa ter alvo tipado, transição válida e efeito material no serviço dono do conteúdo.

```mermaid
erDiagram
    SOCIAL_REPORT ||--o{ MODERATION_CASE : agrupa
    MODERATION_CASE ||--o{ MODERATION_ACTION : decide
    MODERATION_CASE o|--o| COMMENT_MODERATION_STATE : afeta
    MODERATION_CASE o|--o| PUBLICATION_MODERATION_STATE : afeta
    PROFILE ||--o{ USER_RESTRICTION : recebe
    USER_RESTRICTION ||--o{ ADMIN_AUDIT_LOG : audita

    MODERATION_CASE {
      uuid id PK
      text target_type
      uuid target_id
      text status
      text severity
      timestamptz opened_at
      timestamptz resolved_at
    }
    MODERATION_ACTION {
      uuid id PK
      uuid case_id
      text decision
      text reason_code
      text actor_subject_key
      jsonb evidence
    }
    USER_RESTRICTION {
      uuid id PK
      text target_subject_key
      text status
      text reason_code
      timestamptz expires_at
    }
```

`target_type` deve limitar `COMMENT`, `BOOK`, `PUBLICATION`, `PROFILE`; a API valida que o alvo existe no contexto dono. Ocultação fica em estado próprio de comentário/publicação; conteúdo reportado não é removido fisicamente.

### Publicação e estatísticas do publicador

O catálogo é canônico para obra, edição, assets e direito de distribuição. O schema `publisher` é o workflow de quem submeteu, e não uma segunda fonte de verdade bibliográfica.

```mermaid
erDiagram
    PUBLISHER_SUBMISSION ||--|| SUBMISSION_METADATA : descreve
    PUBLISHER_SUBMISSION ||--o{ SUBMISSION_CONTRIBUTOR : credita
    PUBLISHER_SUBMISSION ||--o{ RIGHTS_ATTESTATION : declara
    PUBLISHER_SUBMISSION ||--o{ PUBLICATION_REVIEW : revisa
    PUBLISHER_SUBMISSION ||--o{ STAGED_UPLOAD : envia
    PUBLISHER_SUBMISSION ||--o| PUBLISHED_WORK : vincula
    PUBLISHED_WORK ||--|| BOOK : obra_canonica
    PUBLISHED_WORK o|--o| EDITION : edicao_canonica
    PUBLISHED_WORK ||--o{ DAILY_PUBLICATION_METRIC : agrega

    SUBMISSION_METADATA {
      uuid submission_id PK
      text synopsis
      text language
      text isbn
      text publisher_name
      jsonb keywords
    }
    RIGHTS_ATTESTATION {
      uuid id PK
      uuid submission_id
      text right_type
      text territory
      text evidence_reference
      timestamptz affirmed_at
    }
    PUBLICATION_REVIEW {
      uuid id PK
      uuid submission_id
      text status
      text reviewer_subject_key
      text reason_code
    }
    PUBLISHED_WORK {
      uuid id PK
      uuid submission_id
      uuid book_id
      uuid edition_id
      text publisher_subject_key
      text status
      timestamptz published_at
    }
    DAILY_PUBLICATION_METRIC {
      date day PK
      uuid published_work_id PK
      bigint impressions
      bigint opens
      bigint reads
      bigint likes
      bigint shares
      bigint comments
    }
```

`daily_publication_metric` é projeção reconstruível de `behavior.events` e `social`, nunca fonte de verdade. `published_work` resolve a limitação de inferir titularidade apenas por `submission.catalog_book_id`, inclusive quando houver edição/republicação.

## Plano de migrations recomendado

1. `reader-profile` V3: política de privacidade; migrar `is_public` para defaults e manter a coluna até a API ser migrada.
2. `reader-state` V5: locator/versão/capítulo nullable e índices por `(subject_key, updated_at)`; preencher legados gradualmente.
3. `recommendation` V3/V4: request strategy/seed e item/impression compatíveis; backfill sintético quando necessário.
4. `social` V6/V7: estado de comentário e atividade, com índices de feed por ator e livro.
5. `admin` V4+: casos/ações/restrições tipados e ponte idempotente de sincronização com Keycloak.
6. `publisher` V6+: metadata, contributors, attestation, review, published work e métricas diárias.
7. `behavior` V7: shares/comments nas projeções, ou uma única projeção idempotente no publisher; registrar a decisão de ownership.

Cada migration deve trazer constraints/checks, índices que acompanhem a consulta real, teste de upgrade com dados existentes e teste de autorização. Não criar FK cross-schema por conveniência: publicar eventos ou validar por contrato quando os donos forem diferentes.

## Critérios de aceite por dados

- Dois leitores com sinais distintos produzem requests e itens auditáveis diferentes, com estratégia e versão do modelo registradas.
- Um trecho no feed referencia `analytics.excerpt` elegível e sua entrega é medida sem duplicar texto.
- Perfil privado não expõe atividade, biblioteca, recentes nem grafo social fora da política, inclusive por endpoints administrativos sem papel.
- Bookmark retorna à mesma versão/capítulo; mudança de texto tem fallback explícito, nunca reposicionamento silencioso arbitrário.
- Denúncia gera caso e ações; decisão de ocultar altera a consulta pública, preservando evidência e auditoria.
- Bloqueio é idempotente, auditado e tem estado de confirmação do Keycloak; o banco não armazena credenciais.
- Publicação aprovada possui metadata, contribuição, direito declarado, vínculo canônico e métricas diárias, sem acesso a dados de outros publicadores.

## Referências internas

- [Modelo e regras do catálogo](schema.md)
- [Estado do leitor](reader-state-service.md)
- [Dados sociais](social-service.md)
- [Eventos e agregados comportamentais](behavior-service.md)
- [ADR de publicação Readium](../adr/ADR-011-readium-reader-ready-publication.md)
- [Projeto de storage](../storage/data-model.md)
