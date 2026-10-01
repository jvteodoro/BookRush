# Book analytics service

O `book-analytics-service` owns o schema PostgreSQL `analytics`. Ele lê o
catálogo canônico, mas não copia nem modifica `catalog`. Cada execução e cada
observação mantém a versão física textual que produziu o resultado.

A versão V1 mantém esse contrato e adiciona lineage de artefatos, status explícito
de valor, modelos de corpus/estilo/protótipos/tópicos, embeddings tipados e
ranking explicável por migration aditiva. Os contratos machine-readable ficam em
`config/` e são validados no CI por `scripts/validate-analytics-specs.py`.

O handbook raiz contém a visão consolidada em `docs/analytics-v1.md`; os arquivos
machine-readable permanecem em `../config/` no repositório.

A [auditoria do baseline para V2](analytics-v2-audit.md) registra o que já é
executável, as lacunas confirmadas e a ordem dos beads que evoluem candidates,
features, modelos e seleção sem reinterpretar a V1.

A [implementação e operação V2](analytics-v2-implementation.md) documenta o
runtime Python local, preparação explícita de modelos, gates, scorer experimental
e os limites para iniciar um piloto de corpus.

O [relatório de estado de 2026-10-01](analytics-status-2026-10-01.md) consolida
as evidências reais, comandos de reprodução e pendências que ainda exigem dados
rotulados ou aprovação antes do processamento em massa.

## Features baseline

`TextFeatureCalculator` é determinístico e versionado (`deterministic-text-v1`).
Ele calcula contagens, razões, sinais de diálogo, densidade de pontuação,
tempo estimado e posições relativas em code points. Features unsupported devem
ser omitidas; nenhuma ausência é convertida silenciosamente em zero.

A camada estatística usa fórmulas explícitas e não pretende ser uma medida
científica universal: lexical diversity é `tipos/tokens`, rare word ratio é a
fração de tokens com frequência 1 e readability é uma estimativa Flesch com
sílabas aproximadas. O analyzer e a versão da fórmula devem acompanhar cada
observação.

Sentimento/emoção possui SPI local (`EmotionClassifier`). O adaptador padrão fica
desabilitado e retorna `unsupported`, nunca um vetor de zeros. Um modelo só pode
ser habilitado quando seu artefato, versão e checksum forem configurados; o
startup não baixa modelos.

A agregação de capítulos/documentos calcula médias somente sobre observações
presentes e finitas. Valores `unsupported` não entram como zero, preservando a
diferença entre ausência de evidência e score baixo.

O gerador de candidates usa janelas de sentenças, stride configurável e filtros
baratos de boilerplate. Ele persiste offsets half-open em Unicode code points e
`text_sha256`; a mesma versão textual/configuração/generator version produz o
mesmo conjunto ordenado.

O pruning `CandidatePruner` é configurado por versão, registra motivos
`TOO_SHORT`, `TOO_LONG`, `EMPTY` e `DUPLICATE`, e devolve métricas aceitas/rejeitadas
antes de qualquer modelo caro.

`ExcerptFeatureObservationFactory` transforma o resultado dos analyzers em
observações tipadas com `analysisRunId`, `excerptId`, analyzer e timestamp. A
ordenação por código e a identidade da execução tornam o replay determinístico;
a persistência deve usar a chave composta da migration para evitar duplicação.

O ranker baseline usa pesos versionados e devolve contribuições por feature para
explicação. O resultado é um `candidate_score` limitado a `[0,1]`, não uma
probabilidade de engajamento nem uma decisão jurídica/editorial.

Consulte o [dicionário de features](feature-dictionary.md) para distinguir
medições, scores de modelo e sinais de seleção de produto.

## Fases 1–8

As fases iniciais estão implementadas de forma incremental: segurança OIDC
com audience/scope próprios, features estruturais e PT/EN, corpus-frequency e
normalização de estilo versionáveis, embeddings BGE-M3 de excerpt/capítulo/
documento, protótipos semânticos, NLI narrativo/emocional opcional e ranker
heurístico explicável. O classificador supervisionado é treinado somente por
comando explícito com `scripts/train-topic-classifier.py`; o artefato permanece
`EXPERIMENTAL` até avaliação por work-level split. As fases 9–10 (piloto/bulk e
avaliação científica/GO) continuam deliberadamente separadas e não são
iniciadas pelo worker.

O benchmark de latência e projeção de custo do NLI local está em
[`docs/analytics/nli-local-benchmark.md`](../../../docs/analytics/nli-local-benchmark.md).

A milestone [Excerpt Quality Learning V1](annotation-quality-learning-v1.md)
adiciona campanhas, anotação cega, leases, métricas QA e datasets imutáveis.
