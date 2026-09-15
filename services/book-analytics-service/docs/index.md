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
