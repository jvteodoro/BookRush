# Relatório de estado dos Analytics — 2026-10-01

Este documento registra o resultado da implementação e da validação operacional
da camada Content Analytics. O serviço `book-analytics-service` é o único dono
do schema `analytics`; o catálogo continua dono das entidades bibliográficas e
das versões físicas de texto.

## Entregue

- Geração idempotente de excerpts com offsets em code points Unicode, intervalos
  half-open, hash do texto e referência à `book_asset_version`.
- Features estruturais, lexicais, legibilidade e posições relativas.
- Runtime Python interno para fastText, embeddings BGE-M3 e inferência NLI.
- Embeddings de excerpts e documentos armazenados como artefatos privados S3,
  com modelo, dimensão, hash de entrada e lineage no PostgreSQL.
- Scores de protótipos semânticos versionados. Eles são similaridades, não
  probabilidades.
- NLI narrativo e emocional com os valores brutos entailment, neutral e
  contradiction, além de `support` e `confidence` derivados.
- Corpus-frequency versionado para inglês e português.
- Normalização de estilo registrada para inglês.
- Segurança OIDC do serviço com issuer, audience, scope e grupos validados.
- Benchmark local do NLI e documentação de operação, cache de modelos e
  timeouts de CPU.

## Evidências no ambiente

Foi executado um job controlado com duas versões textuais Gutenberg já
normalizadas no catálogo. O job terminou com `2/2` itens bem-sucedidos e zero
falhas. A base contém atualmente:

| Artefato | Quantidade |
| --- | ---: |
| Observações NLI narrativas/emocionais | 1.600 |
| Scores de protótipos semânticos | 138 |
| Embeddings de excerpts | 65 |
| Embeddings de documentos | 1 |
| Modelos corpus-frequency | 2 |
| Modelos de normalização de estilo | 1 (EN) |

O snapshot inglês `gutenberg-pilot-20260930` contém 202.813 tokens e 10.761
termos. O snapshot português `gutenberg-pt-pilot-20260930` contém 162.419
tokens e 19.746 termos. Os dois artefatos estão no bucket privado `books-ml`.
O artefato PT possui SHA-256
`e2bd9fc35ee1aa25f7468603a4c2e8a39750cfef014bf868b095b533f73f577c`.

## Como reproduzir as verificações

No checkout do repositório:

```bash
python3 scripts/validate-analytics-specs.py
python3 -m py_compile scripts/benchmark-nli-local.py \
  scripts/publish-corpus-artifact.py scripts/train-topic-classifier.py
git diff --check
```

O teste Java deve ser executado em um container Maven, pois o host não precisa
ter Maven instalado:

```bash
docker run --rm \
  -v "$PWD/platform:/workspace/platform" \
  -v "$PWD/services/book-analytics-service:/workspace/service" \
  -w /workspace/service maven:3.9-eclipse-temurin-21 \
  bash -lc 'mvn -q -f /workspace/platform/pom.xml install -DskipTests && mvn -q test'
```

A publicação de um novo corpus é explícita e não ocorre no startup:

```bash
docker run --rm --network bookrush_bookrush --env-file .env \
  -v /caminho/manifest.txt:/tmp/manifest.txt:ro \
  -v "$PWD/scripts/publish-corpus-artifact.py:/tmp/publish.py:ro" \
  --entrypoint python bookrush/book-analytics-runtime:local /tmp/publish.py \
  --language pt --snapshot nome-do-snapshot --manifest /tmp/manifest.txt
```

O manifesto deve conter apenas arquivos locais previamente verificados. O
comando imprime a chave S3, quantidade de tokens, vocabulário e SHA-256 para
registro em `analytics.corpus_frequency_model`.

## Pendências reais

Estas pendências permanecem abertas no bead `bookrush-vc0a` porque exigem
evidência adicional, dados rotulados ou decisão de operação:

1. gerar a normalização de estilo PT a partir de observações PT produzidas pelo
   pipeline, e não apenas do snapshot lexical;
2. montar dataset supervisionado com várias obras, subjects canônicos e
   embeddings de documentos; treinar e avaliar o `BOOK_TOPIC_CLASSIFIER_V1`
   com divisão por obra;
3. revisar manualmente 50 excerpts EN e 50 PT;
4. executar os gates de 10, 50 e 100 livros e registrar throughput, storage e
   avaliação;
5. aprovar formalmente GO/NO-GO antes do piloto de 500–1000 livros.

Não existe autorização para iniciar processamento em massa. O ranker continua
experimental e não representa qualidade, engajamento, probabilidade de leitura
ou decisão editorial.

## Fontes e rastreabilidade

- Contratos: `services/book-analytics-service/config/`.
- Migrations: `services/book-analytics-service/src/main/resources/db/migration/`.
- Runtime Python: `services/book-analytics-runtime/`.
- Benchmark: `docs/analytics/nli-local-benchmark.md`.
- Auditoria detalhada: `analytics-v2-audit.md`.
- Runbook: `operations/runbook.md`.
