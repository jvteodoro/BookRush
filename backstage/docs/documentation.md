# TechDocs, APIs, ADRs e diagramas

Cada Component documentável tem mkdocs.yml, docs/ e techdocs-ref. O handbook serve
docs/ existente; documentos novos de cada serviço ficam junto do código.
A base backstage/mkdocs.base.yml fixa techdocs-core e extensões Markdown. O template
leva cópia da base para funcionar num repositório independente.

## Diagrams-as-code

Use fenced code `mermaid`. A CI converte blocos em SVG pelo Mermaid CLI/Chromium
local, antes do MkDocs. Não envia diagramas a serviços externos e não depende de
JavaScript externo na página TechDocs. O SVG é derivado; o Markdown continua canônico.
O build falha se Mermaid não compilar. Preview e backend local usam o mesmo hook.

## APIs

API Catalog carrega $text de api/openapi.yaml relativo ao catalog-info.yaml.
Não lê diretamente produção durante navegação e não guarda cópia manual de endpoints.
Use o exportador contra Springdoc da **mesma revisão** que está no PR:

```bash
python3 backstage/scripts/export-openapi.py   --url http://localhost:18090/ingestion-docs/v3/api-docs   --output services/book-ingestion-service/api/openapi.yaml
```

O validador verifica OpenAPI e relações. Não comprova sozinho equivalência com o
código; exportação e revisão do diff são obrigatórias para mudanças de API.
AsyncAPI só será registrado quando houver mensagens reais. api/README.md explica
por que não existe contrato de eventos dos serviços atuais.

## ADRs

O handbook docs/adr contém o formato e decisões globais. Decisões de ingestão
permanecem em docs/ingestion/adr. Use uma nova decisão para substituir outra; preserve
histórico, alternativas técnicas, consequências e riscos. Mermaid também funciona aqui.

## Publicar em S3 pela CI

A identidade do publicador precisa PutObject/GetObject/ListBucket somente no bucket
TechDocs; o backend precisa apenas leitura. Não reutilize a credencial administrativa
dos livros. Bucket deve existir, privado, antes da publicação.

```bash
# Injete AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION,
# TECHDOCS_BUCKET e TECHDOCS_S3_ENDPOINT via Credentials da CI.
bash backstage/scripts/publish-docs.sh
```

A pipeline padrão valida e constrói; publicação requer esses valores operacionais
explícitos. O script usa techdocs-cli generate/publish com os cinco entity triplets.
As configurações produção não ativam builder local por acidente.
