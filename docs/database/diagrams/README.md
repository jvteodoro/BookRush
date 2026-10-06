# Diagramas E/R exportados

Estes SVGs são artefatos vetoriais prontos para relatórios, apresentações e
documentação externa. As fontes canônicas continuam nos blocos Mermaid dos
documentos indicados; ao alterar uma fonte, regenere o SVG correspondente com
o Mermaid CLI versionado em `backstage/node_modules/.bin/mmdc`.

| Diagrama | Cobertura | Fonte Mermaid |
| --- | --- | --- |
| [Catálogo bibliográfico](catalog-er.svg) | `catalog`: obra, edição, autores, assets, versões, processamento e ingestão. | [schema.md](../schema.md) |
| [Domínios de produto](product-domains-er.svg) | `reader_profile`, `reader_state`, `recommendation`, `social`, `admin` e `publisher`. | [schema.md](../schema.md) |
| [Eventos comportamentais](behavior-er.svg) | `behavior`: eventos, outbox, rejeições e projeções diárias. | [schema.md](../schema.md) |
| [Analytics de conteúdo](analytics-content-er.svg) | `analytics`: jobs, runs, excerpts, features e embeddings. | [data-model.md](../../analytics/data-model.md) |
| [Anotação de qualidade](analytics-annotation-er.svg) | `analytics`: campanhas, atribuições, rótulos, adjudicação e datasets. | [data-model.md](../../analytics/data-model.md) |
| [Storage e linhagem](storage-er.svg) | Relações de assets, versões, intents e processamento em `catalog`. | [storage-model.md](../storage-model.md) |

`book_content` não possui tabelas no DDL atual; portanto, não há diagrama E/R
para esse schema. Os demais relacionamentos cross-schema são contratuais e são
documentados em [schema.md](../schema.md), não como FKs inexistentes.
