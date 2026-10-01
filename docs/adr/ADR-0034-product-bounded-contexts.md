# ADR 0034 — Bounded contexts da plataforma de produto

Status: Accepted  
Date: 2026-10-01

## Context

O cliente BookRush precisa compor catálogo, conteúdo de leitura, estado do leitor, perfil, social, comportamento e recomendações. O catálogo e o analytics já possuem ownership próprio e não podem virar uma base compartilhada.

## Decision

Adotamos serviços separados para `content`, `reader_state`, `reader_profile`, `social`, `behavior`, `recommendation`, `publisher`, `admin` e `reader-bff`. Cada serviço mantém schema e migrations próprios. O BFF somente compõe respostas e não possui tabelas de domínio.

A identidade persistida é a tupla opaca `(identity_issuer, identity_subject)`, derivada do JWT validado. E-mail, username e headers de proxy não são chaves de usuário. IDs de outros contextos são referências externas e são validados por API ou eventos.

Eventos de comportamento são append-only, idempotentes por `event_id`/`(source, idempotency_key)`, sem texto de livro ou PII por padrão. O armazenamento inicial é PostgreSQL + outbox/polling; um broker continua sendo uma seam futura.

O feed deve carregar `recommendationRequestId`, `impressionId`, `modelVersion` e `rank` até o cliente. Uma impressão só é registrada após o limiar de viewability. Fatos server-side complementam eventos do cliente.

## Consequences

- Rebuild e deploy podem evoluir cada serviço de forma independente.
- Referências entre serviços exigem contratos HTTP/eventos e testes de contrato.
- Consultas compostas passam pelo BFF e têm timeouts/degradação explícitos.
- Dados históricos de comportamento não são editados; agregados podem ser recalculados.
- Produto e administração permanecem em realms Keycloak distintos.

## Alternatives Considered

Uma base única com entidades JPA compartilhadas reduziria o código inicial, mas quebraria ownership, retenção e evolução independente. Kafka foi adiado até existir evidência de throughput que justifique a operação.
