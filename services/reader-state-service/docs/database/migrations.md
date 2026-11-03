# Reader-state migrations

## V7 and V8 — feed-origin reading sessions

`V7__session_book_and_attribution.sql` adds nullable `book_id` to
`reader_state.session`, preserving compatibility with sessions created before
feed-origin attribution.

`V8__session_recommendation_correlation.sql` adds nullable
`recommendation_item_id` and `impression_id`. New sessions persist the IDs from
the authenticated request headers; ending a session reuses those IDs when the
client does not resend them. Direct, search and library sessions remain
uncorrelated (`NULL`). Both migrations are additive and do not rewrite or
delete existing sessions.

## V9 — livros vistos por perfil

`V9__seen_books.sql` cria `reader_state.seen_book`, com PK
`(subject_key, book_id)`, `first_seen_at`/`last_seen_at` timestamptz e restrição
last ≥ first. A chave composta cobre a consulta privada em lotes de até 100 IDs;
não é necessário um índice adicional ou carregar todo o histórico.

O backfill inclui somente progresso legado com percent > 0 ou posição > 0,
usando updated_at como evidência temporal disponível. Abertura antiga com
progresso zero não é prova de carregamento; recent permanece intacta.
Biblioteca, progresso, planta, sessões e histórico não são apagados. Não há
FK para o catálogo: remover um conteúdo não apaga seu registro privado.
