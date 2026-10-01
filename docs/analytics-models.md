# Preparação de modelos Content Analytics

O serviço não baixa modelos no startup. A seleção V1 está congelada em
`services/book-analytics-service/config/model-artifacts-v1.json`: ela contém
as licenças, URLs/revisões imutáveis e SHA-256 de cada arquivo necessário. Não
substitua arquivos ou hashes por variáveis de ambiente.

```bash
ANALYTICS_MODEL_CACHE_DIR=/caminho/seguro/para/modelos \
  make analytics-models-fetch

# Em uma máquina sem rede, somente verifica o cache previamente preparado.
ANALYTICS_MODEL_CACHE_DIR=/caminho/seguro/para/modelos \
  make analytics-models-verify
```

O layout é `<cache>/<model-id>/<arquivo>`. O `make analytics-models-fetch` é a
única operação que baixa artefatos; ela usa fontes e commits já fixados e só
instala o arquivo após verificar o SHA-256. O cache montado no Compose é
somente leitura para o serviço. BGE-M3 V1 usa o peso `pytorch_model.bin` porque
o commit fixado não publica um peso dense equivalente em `safetensors`; o NLI
usa `model.safetensors`.

Em produção use `ANALYTICS_OFFLINE=true`; ausência ou checksum inválido deve
impedir somente o estágio que requer o artefato, com status `MODEL_UNAVAILABLE`,
sem mascarar a falha como zero. As revisões Hugging Face BGE-M3 e mDeBERTa são
obrigatoriamente commits imutáveis registrados no manifesto antes do Gate 4/5.
Nenhum token Hugging Face ou credencial de storage deve ser salvo no Git.
## Evidência de execução real

Os artefatos do registro V1 foram baixados e verificados em 2026-09-30 no
host de produção. O cache foi instalado no volume Docker `bookrush_analytics_models`
e o runtime operou com `ANALYTICS_OFFLINE=true`; não há download implícito no
startup. A prova controlada com duas versões Gutenberg concluiu o job sem
falhas e confirmou gravação de embeddings BGE-M3 no bucket privado `books-ml`.
