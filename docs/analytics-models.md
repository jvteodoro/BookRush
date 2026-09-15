# Preparação de modelos Content Analytics

O serviço não baixa modelos no startup. Prepare o cache com o mecanismo aprovado
para o ambiente e valide os SHA-256 antes de habilitar os estágios ML. O script
`fetch-analytics-models.sh` falha se algum arquivo estiver ausente e aceita os
hashes por variáveis (`FASTTEXT_LID_SHA256`, `SPACY_EN_SHA256`, `SPACY_PT_SHA256`,
`BGE_M3_SHA256`, `MDEBERTA_NLI_SHA256`).

```bash
ANALYTICS_MODEL_CACHE_DIR=/var/lib/bookrush/models \
  bash scripts/fetch-analytics-models.sh
```

Em produção use `ANALYTICS_OFFLINE=true`; ausência ou checksum inválido deve
impedir somente o estágio que requer o artefato, com status `MODEL_UNAVAILABLE`,
sem mascarar a falha como zero. As revisões Hugging Face BGE-M3 e mDeBERTa são
obrigatoriamente commits imutáveis registrados no manifesto antes do Gate 4/5.
Nenhum token Hugging Face ou credencial de storage deve ser salvo no Git.
