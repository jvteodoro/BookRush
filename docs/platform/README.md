# Plataforma Java compartilhada

`platform/` é um reactor Maven independente dos serviços executáveis. Ele fornece versões alinhadas e pequenos componentes técnicos; não possui banco, Flyway, entidades JPA, scheduler ou regras do catálogo.

## Módulos e grafo

```text
bookrush-platform (pom agregador)
├── bookrush-build-parent (plugins e compilação)
├── bookrush-bom (dependencyManagement)
└── JARs técnicos, consumidos seletivamente
    ├── bookrush-common-core (JDK)
    ├── bookrush-common-web
    ├── bookrush-security-spring-boot-starter
    ├── bookrush-observability-spring-boot-starter
    └── bookrush-test-support (scope test)
```

O parent não importa o BOM e o BOM não herda do parent. O catálogo continua dono dos contratos bibliográficos e a ingestão continua dona de jobs, leases e proveniência. A separação completa e o estado atual estão no [inventário do baseline](baseline-inventory.md) e no [ADR-006](../adr/ADR-006-platform-module-ownership.md).

## Consumo

Um serviço importa o BOM em `dependencyManagement` e declara somente o módulo necessário, sem repetir versões. A versão inicial prevista é `0.1.0-SNAPSHOT`; a publicação deve ocorrer em um repositório Maven controlado ou no repositório local isolado de CI. Bibliotecas não usam o goal `repackage`.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.bookrush</groupId>
      <artifactId>bookrush-bom</artifactId>
      <version>${bookrush.platform.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

## Build e evolução

```bash
mvn -f platform/pom.xml clean verify
mvn -f platform/pom.xml install -DskipTests
```

Consumidores devem ser testados em um repositório Maven vazio previamente populado pelo `install` da plataforma. Overrides de versões exigem justificativa no POM e atualização do changelog; serviços podem adotar releases diferentes.

O exemplo compilável está versionado em `examples/platform-consumer/` (fora do
site TechDocs, para poder ser usado diretamente pelo Maven).
mostra esse consumo sem parent e sem acesso aos fontes dos serviços.

## Elegibilidade de helpers

Um helper só entra aqui quando há comportamento técnico estável, consumidor real, teste de borda e ausência de ownership de domínio. Identidade técnica, correlation ID e hash por stream são candidatos; `BaseEntity`, CRUD genérico, `User` universal e `Utils` sem limite não são.

Nesta etapa `Sha256` já é consumido pelo `AssetNormalizeJob` para o hash do texto
normalizado; ele usa UTF-8 e limite explícito sem alterar o contrato do catálogo.
`TechnicalIdentity` e `CorrelationId` aguardam os módulos de segurança e
observabilidade que os consumirão. O starter de observabilidade usa
`CorrelationId` para validar `X-Correlation-ID`, limpa MDC em `finally` e não
adiciona coletor ou label de alta cardinalidade.

`common-web` fornece apenas `TechnicalErrorResponse` e `TechnicalErrors.safe`:
mensagens fixas para conflitos `CANONICAL_CONFLICT` e
`IDEMPOTENCY_CONFLICT`, sem stacktrace ou detalhes internos. O módulo não
registra handler global; cada serviço preserva seus payloads e status existentes
e escolhe explicitamente quando adaptar o valor.
