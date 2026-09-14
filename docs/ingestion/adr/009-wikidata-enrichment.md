# ADR-009 — Enriquecimento Wikidata reproduzível

Status: Accepted  
Date: 2026-09-14

## Context

Wikidata é útil para QIDs, datas e identificadores, mas não é autoridade jurídica
nem deve provocar uma requisição por livro. Resultados externos precisam ser
reprocessáveis e auditáveis.

## Decision

Nesta fase, Wikidata será consumido por snapshot/export verificável ou cache
persistido em `source_record`/RAW, com checksum, data, versão do parser e
limites de tamanho. O escopo inicial é QID de autor/obra, datas de nascimento e
morte sustentadas, idioma original, movimentos literários e relações de IDs.
Cada valor aplicado ao catálogo usa `field_provenance` com regra, confiança,
fonte e evidência. Redirects/deletes são observações e não removem entidades.

Loop HTTP por livro, scraping de páginas humanas e preenchimento de campos
ambíguos ficam proibidos. Falha de snapshot deixa o job retryable e não apaga o
valor canônico anterior.

## Consequences

A aquisição exige armazenar manifesto e espaço RAW, mas permite replay offline.
Propriedades novas precisam de decisão específica; presença de QID não concede
licença ou autorização de distribuição.
