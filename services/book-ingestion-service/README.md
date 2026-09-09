# Book ingestion service

Serviço Spring Boot separado para aquisição Gutenberg-first, staging local e
aplicação idempotente no catálogo. O processo não inicia jobs no boot:
`INGESTION_SCHEDULER_ENABLED` permanece `false` por padrão.

## Perfis de processo

`INGESTION_ROLE=API` expõe a administração; `WORKER` executa consumidores; `ALL`
é o perfil local. A separação é uma fronteira de responsabilidade, não um
atalho de segurança. Os jobs e filas serão implementados nos beads seguintes.

## Executar

```bash
./mvnw test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

O wrapper usa Maven local quando disponível e, caso contrário, executa Maven em
um container. A validação reproduzível do serviço não depende de instalar Maven
no host:

```bash
docker build -t bookrush/book-ingestion-service:local .
docker run --rm --network bookrush_default \
  -e DB_HOST=postgres \
  -e INGESTION_ROLE=API \
  bookrush/book-ingestion-service:local
```

O serviço usa Java 21, PostgreSQL para o JobRepository futuro e DuckDB somente
para staging. Os valores padrão são de desenvolvimento e não contêm credenciais
reais. Consulte `docs/ingestion/` para ownership, limites, fontes e contratos.

O módulo ainda não adquire fontes nem cria entidades canônicas: essas operações
serão habilitadas apenas pelos jobs e portas dos beads posteriores.
