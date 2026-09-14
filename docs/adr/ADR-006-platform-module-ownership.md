# ADR-006 — Plataforma Java compartilhada e ownership de contratos

Status: Accepted  
Date: 2026-09-14

## Context

Catálogo, ingestão e analytics repetem necessidades técnicas, mas seus dados e operações pertencem a domínios diferentes. Uma biblioteca compartilhada não deve virar um segundo dono de tabelas, migrações ou regras bibliográficas.

## Decision

Será mantido um reactor Maven isolado em `platform/`. O reactor agrega módulos e não é usado como parent obrigatório dos serviços:

* `bookrush-platform` agrega o reactor;
* `bookrush-build-parent` concentra apenas plugins e compilação;
* `bookrush-bom` gerencia versões, importando o BOM do Spring Boot e BOMs externos justificados;
* JARs técnicos (`common-core`, `common-web`, segurança, observabilidade e suporte de testes) são consumidos seletivamente.

O build parent não importa o BOM e o BOM não herda do build parent, evitando o ciclo parent → BOM → parent. Contratos de catálogo só serão extraídos quando a mesma API os governar; entidades JPA, repositories e migrations continuam nos serviços proprietários. A primeira versão é `0.1.0-SNAPSHOT`, com evolução opt-in e compatibilidade documentada.

## Alternatives Considered

* Colocar classes técnicas em cada serviço mantém duplicação e divergência.
* Criar um parent único que também gerencia runtime acopla plugins, starters e domínio.
* Compartilhar entidades JPA viola ownership e torna migrações dependentes.

## Consequences

Consumidores continuam aplicações independentes e podem instalar a plataforma em um repositório Maven isolado. O custo é manter versões publicadas e testar compatibilidade entre módulos. A adoção de cada JAR permanece explícita.

## Risks

Uma API técnica pode ganhar dependências de Spring ou de fornecedor por conveniência. Revisões devem verificar dependências transitivas e manter `common-core` somente com JDK.

## References

* [Inventário do baseline](../platform/baseline-inventory.md)
* [Guia da plataforma](../platform/README.md)
