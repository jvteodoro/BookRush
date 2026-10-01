# Content Analytics V1 — contratos versionados

Os arquivos YAML/JSON deste diretório são contratos de dados e modelos. Não altere códigos de feature sem atualizar a versão do contrato e registrar um ADR. A validação local é executada por `scripts/validate-analytics-specs.py`.

O serviço continua sendo o único owner do schema `analytics`. Os códigos distinguem `MEASUREMENT`, `MODEL_SCORE` e `PRODUCT_SCORE`; similaridade e suporte NLI não são probabilidades comportamentais.

Modelos são preparados explicitamente. `model-artifacts-v1.json` é o lock de
artefatos: fixa licenças, revisão imutável e SHA-256 de cada arquivo. Execute
`make analytics-models-fetch` fora do runtime e `make analytics-models-verify`
antes de montar o cache somente leitura. `ANALYTICS_OFFLINE=true` impede
downloads implícitos; ausência/corrupção deve resultar em `MODEL_UNAVAILABLE`.
