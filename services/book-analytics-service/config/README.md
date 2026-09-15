# Content Analytics V1 — contratos versionados

Os arquivos YAML/JSON deste diretório são contratos de dados e modelos. Não altere códigos de feature sem atualizar a versão do contrato e registrar um ADR. A validação local é executada por `scripts/validate-analytics-specs.py`.

O serviço continua sendo o único owner do schema `analytics`. Os códigos distinguem `MEASUREMENT`, `MODEL_SCORE` e `PRODUCT_SCORE`; similaridade e suporte NLI não são probabilidades comportamentais.

Modelos são preparados explicitamente. `ANALYTICS_OFFLINE=true` impede downloads implícitos; revisões Hugging Face precisam ser imutáveis antes de qualquer cálculo em lote.
