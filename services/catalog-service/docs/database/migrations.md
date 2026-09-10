# Migrations

Flyway no catalog-service é o único mecanismo. Não edite migrations aplicadas; crie nova versão. A inicialização do catálogo executa migração antes do Hibernate. Teste instalação limpa e upgrade em banco descartável. Nunca faça repair sem confrontar DDL e histórico.
