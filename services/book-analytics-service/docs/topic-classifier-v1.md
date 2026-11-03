# Topic classifier V1

O classificador de tópicos é uma camada experimental sobre o embedding de
documento BGE-M3. O dataset usa somente subjects metadata-derived e deve ter
`work_id`, `embedding` e `labels`; o split é por `work_id`, evitando vazamento
entre edições da mesma obra.

`config/topic-taxonomy-v1.json` congela os códigos canônicos e a política de
suporte. `scripts/train-topic-classifier.py` valida os rótulos, treina
One-vs-Rest Logistic Regression com `class_weight=balanced`, penalização L2,
hash do dataset/taxonomia e estado `EXPERIMENTAL`. O artefato não é carregado
no worker automaticamente e não é promovido por possuir métricas de treino.

`scripts/evaluate-topic-classifier.py` calcula macro/micro F1, precisão,
recall, suporte, PR-AUC e Brier por rótulo. Ausência de suporte positivo falha
a avaliação; não há score sintético para completar um gate. A promoção exige
revisão humana e um artefato `ACCEPTED` explícito.

## Spoiler

`spoiler-components-v1` persiste apenas `position`, `resolution` e `revelation`
como componentes explicáveis. Eles não são uma probabilidade calibrada e o
sistema não expõe `spoiler_probability` sem dataset anotado e validação.
