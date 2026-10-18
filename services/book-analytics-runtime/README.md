# BookRush Analytics Runtime

Python 3.12 bounded worker for pinned spaCy PT/EN NLP. It makes no network requests and returns `MODEL_UNAVAILABLE` until an operator has prepared and installed the matching locked wheel. Java remains the owner of `analytics`; this runtime only performs bounded inference over an internal API.

The linguistic endpoint exposes POS ratios, finite-verb density, dependency
depth/distance, entity counts and bounded referential signals. The
`unresolved_reference_proxy` is deliberately only a proxy: it counts pronoun or
demonstrative tokens that occur before any detected named entity in the excerpt.
It must not be described or consumed as coreference resolution. Model loading
remains local-only and all unavailable/error states are explicit.

## Inferência em lote

`POST /v1/nli` permanece disponível para compatibilidade. O runtime também
expõe `POST /v1/nli/batch`, recebendo itens com `id`, `text` e `hypothesis`.
O limite padrão é 32 itens por requisição (`ANALYTICS_NLI_BATCH_MAX_ITEMS`),
com status individual e `id` preservado. O serviço Java divide lotes maiores,
persiste somente resultados `VALID` e pode retomar itens que falharam sem
duplicar observações.

`ANALYTICS_RUNTIME_DEVICE=auto` usa CUDA quando a imagem contém PyTorch CUDA e
o dispositivo está disponível; caso contrário usa CPU. Esta imagem ainda é
construída com `torch==2.7.1+cpu`. A variante CUDA foi construída em
18/10/2026, mas o PyTorch retornou `torch.cuda.is_available() == false` embora
`nvidia-smi` funcionasse no host/container; ela não está validada para
produção. O Compose não assume GPU por padrão para continuar funcionando em
agentes sem NVIDIA Container Toolkit.

Para validar a variante CUDA no host com NVIDIA, use o override somente depois
de confirmar `nvidia-smi` e o runtime NVIDIA:

```bash
docker compose --env-file /run/bookrush.env \
  -f infrastructure/compose.yaml \
  -f infrastructure/compose.analytics-gpu.yaml \
  build book-analytics-runtime
docker compose --env-file /run/bookrush.env \
  -f infrastructure/compose.yaml \
  -f infrastructure/compose.analytics-gpu.yaml \
  up -d book-analytics-runtime
```

O override não deve ser aplicado a agentes sem GPU. A primeira execução deve
usar `ANALYTICS_EMBEDDINGS_BATCH_SIZE=4` e `ANALYTICS_NLI_BATCH_SIZE=8`, com
monitoramento de VRAM e logs de OOM antes de aumentar os lotes.
