# Analytics V1 — contrato operacional

A entrada de cada observação é uma versão textual normalizada (`book_asset_version`).
Offsets são code points Unicode e intervalos `[start,end)`. A migration V6 adiciona
lineage de modelos, status de valor, modelos de corpus/estilo/protótipos/tópicos,
embeddings e ranking.

Os códigos e fórmulas estão em `config/analytics-spec-v1.yaml`; a validação é feita
por `python3 scripts/validate-analytics-specs.py`. Medições, scores de modelo e
scores de seleção permanecem semanticamente separados. NLI grava entailment,
neutral, contradiction, support e confidence; support não é probabilidade de
comportamento. Nenhuma métrica genérica de `interestingness` ou
`engagement_probability` é produzida.

A política de idiomas completa é EN/PT. Para outras línguas, métricas estruturais e
BGE-M3 podem ser executadas; métricas dependentes de spaCy retornam
`UNSUPPORTED`, nunca zero.
