# Content Analytics V1

O `book-analytics-service` é o único owner do schema `analytics`. A versão V1
mantém a API e os jobs Spring existentes e acrescenta contratos versionados em
`services/book-analytics-service/config/`. O texto de entrada é sempre uma
`book_asset_version` normalizada; offsets são code points Unicode e intervalos
half-open `[start,end)`.

Medições estruturais/lexicais, scores de modelos e scores de seleção são categorias
distintas. O ranker é apenas uma ordenação heurística explicitamente não validada;
não representa qualidade, engajamento ou probabilidade de spoiler.

## Contratos e modelos

`analytics-spec-v1.yaml` define códigos e fórmulas. `model-registry-v1.yaml`
define fastText, spaCy PT/EN, BGE-M3 e NLI. Revisões de modelos devem ser
imutáveis e acompanhadas de SHA-256 antes de cálculo em lote. O pipeline não baixa
modelos no startup e funciona com `ANALYTICS_OFFLINE=true` quando artefatos não
estão disponíveis.

## Persistência

Migrations V1–V5 existentes são preservadas. V6 é aditiva e registra artefatos,
status de valor (`VALID`, `UNSUPPORTED`, `INVALID_INPUT`, `INSUFFICIENT_SAMPLE`,
`MODEL_UNAVAILABLE`, `ERROR`), modelos de corpus/estilo/protótipos/tópicos,
embeddings tipados e ranking explicável. Vetores grandes permanecem em storage S3
compatível; PostgreSQL mantém identidade, dimensão, hash e localização.

## Gates

Use `VALIDATION_PLAN_V1.md` para gates 1–8. Nenhum job de corpus completo é iniciado
automaticamente; a revisão de prontidão precisa registrar GO explícito.
