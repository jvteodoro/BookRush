# Migrations do social-service

O `social-service` é o proprietário do schema PostgreSQL `social` e aplica suas
migrations com Flyway durante a inicialização. Migrations já publicadas são
imutáveis; alterações de modelo devem ser adicionadas em uma nova versão e
validadas tanto em banco vazio quanto como upgrade.

## V7 — ciclo de moderação de denúncias

A migration `V7__report_moderation_lifecycle.sql` transforma
`social.reports` em uma fila administrativa com ciclo de vida explícito:

- `OPEN`: aguardando triagem;
- `IN_REVIEW`: denúncia em análise por um operador;
- `RESOLVED`: denúncia tratada;
- `DISMISSED`: denúncia considerada improcedente.

Ela adiciona `reviewed_by_subject`, `reviewed_at`, `resolved_at` e
`resolution_note`. Decisões do Admin Console atualizam esses campos e também
gravam um evento `REPORT_STATUS_CHANGED` no `admin.audit_log`. A restrição de
estado impede valores fora do contrato, e o índice por estado e data atende a
fila ordenada de moderação.

Durante o upgrade, estados históricos desconhecidos são normalizados para
`OPEN`, preservando a denúncia para nova avaliação. Nenhuma denúncia,
comentário ou identidade existente é removida.

## V8 — remoção pelo autor

`V8__comment_author_deletion.sql` adiciona `author_deleted_at` nullable em
`social.comments`, preservando conteúdo, autoria, estado de moderação e
referências de denúncias. Registros legados continuam ativos (`NULL`). Um índice
parcial por livro/data/ID cobre a leitura de comentários disponíveis. A API
preenche o instante no servidor somente após confirmar o subject autenticado;
repetir DELETE não altera o instante original. A marca não pode ser revertida
por restauração administrativa.

O teste `scripts/test-admin-user-management-e2e.sh` aplica V1–V7 em um banco
PostgreSQL 16 descartável com comentário legado, valida upgrade V8 e também o
boot normal do social-service em banco vazio com todas as migrations Flyway.

## V9 — índice para seguidores recebidos

`V9__follower_count_index.sql` adiciona índice por `social.follows.followed`.
A chave primária existente `(follower, followed)` continua garantindo unicidade;
não são criadas linhas de contagem nem copiados dados. A contagem é calculada por
`count(*)` no banco. A migration preserva relações existentes; o E2E valida
upgrade com relação legada e boot Flyway em PostgreSQL descartável.

## V10 — avaliações de livros

`V10__book_ratings.sql` cria `social.book_rating`, com chave composta
`(subject_key, book_id)`, stars SMALLINT restrito a 1–5 e timestamps
timestamptz. O índice por livro inclui stars para calcular count/avg; não há
FK entre schemas de serviços. A existência do livro é validada via catálogo.
Upsert concorrente preserva uma nota por leitor/livro; repetição da mesma nota
não altera updated_at. A média e a nota do leitor vêm da mesma consulta.
Nenhuma tabela anterior é alterada. O E2E cobre boot vazio, upgrade V9 e
validação Flyway em segundo boot.
