# Migrations

Flyway no catalog-service é o único mecanismo. Não edite migrations aplicadas; crie nova versão. A inicialização do catálogo executa migração antes do Hibernate. Teste instalação limpa e upgrade em banco descartável. Nunca faça repair sem confrontar DDL e histórico.

V16 cria a extensão `pg_trgm` e índices de busca textual/parcial para obras,
autores e assuntos. A consulta paginada usa esses índices sem alterar o
modelo bibliográfico. A extensão é instalada explicitamente no schema `public`
e os índices usam `public.gin_trgm_ops`, para que os schemas temporários usados
pelos testes de upgrade encontrem o mesmo operador. O índice de texto usa uma
concatenação com `coalesce`, em vez de `concat_ws`, porque expressões de índice
no PostgreSQL precisam ser imutáveis.

V17 cria `catalog.book_review_audit`, o ledger append-only das decisões
editoriais de aprovação e revogação de livros. Cada evento guarda o livro, os
estados anterior e novo, o subject autenticado, motivo opcional e timestamp;
essa tabela sustenta o histórico administrativo e não deve ser apagada quando
uma aprovação for revogada.
