# Migrations

Flyway no catalog-service é o único mecanismo. Não edite migrations aplicadas; crie nova versão. A inicialização do catálogo executa migração antes do Hibernate. Teste instalação limpa e upgrade em banco descartável. Nunca faça repair sem confrontar DDL e histórico.

V16 cria a extensão `pg_trgm` e índices de busca textual/parcial para obras,
autores e assuntos. A consulta paginada usa esses índices sem alterar o
modelo bibliográfico.
