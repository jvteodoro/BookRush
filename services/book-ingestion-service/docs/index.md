# book-ingestion-service

Adquire RDF e EPUB do Gutenberg, persiste snapshots RAW e coordena jobs, normalização TXT e chapters.json.

Leia [arquitetura](architecture/overview.md), [desenvolvimento](development/local-development.md), [API](api.md) e [runbook](operations/runbook.md).

Documentação transversal detalhada: [handbook](https://github.com/jvteodoro/BookRush/tree/main/docs).

## Resiliência do catálogo Gutenberg

Consultas ao catálogo recebem uma única repetição após falha de I/O, com espera
curta. Respostas HTTP não bem-sucedidas e interrupções não são repetidas: a
interrupção restaura o estado da thread e a falha sobe para o workflow de jobs,
que permanece responsável pela política durável de tentativas.
