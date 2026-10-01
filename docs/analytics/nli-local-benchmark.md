# Benchmark do NLI local

O NLI do BookRush é executado pelo `book-analytics-runtime` com o modelo
`mDeBERTa-v3-base-mnli-xnli` preparado no cache local. O benchmark pode ser
reproduzido sem acessar um provedor externo:

```bash
docker run --rm \
  --network container:bookrush-book-analytics-runtime-1 \
  -v "$PWD/scripts/benchmark-nli-local.py:/tmp/benchmark.py:ro" \
  --entrypoint python bookrush/book-analytics-runtime:local \
  /tmp/benchmark.py --base-url http://127.0.0.1:8092 \
  --repetitions 5 --warmups 1 --nli-calls 16 --hourly-cost 1 --json
```

`--hourly-cost` é um parâmetro de projeção, não um preço embutido no
software. Para uma máquina própria, informe o custo horário de energia ou o
valor contábil escolhido. Para uma VM, use o preço/hora da instância.

## Resultado observado

Execução em 2026-09-30 no runtime local, CPU, três amostras por tamanho e uma
requisição de aquecimento. O arquivo bruto está em
`docs/analytics/nli-local-benchmark-2026-09-30.json`.

| Texto | Latência média por chamada | Chamadas por excerpt | Tempo projetado por excerpt | Tempo por 100 excerpts |
|---:|---:|---:|---:|---:|
| 120 palavras | 0,152 s | 16 | 2,43 s | 4,06 min |
| 300 palavras | 0,387 s | 16 | 6,18 s | 10,31 min |
| 800 palavras | 2,279 s | 16 | 36,46 s | 60,77 min |

As 16 chamadas são nove hipóteses narrativas e sete emoções, executadas
sequencialmente para cada excerpt. Com o limite padrão de 50 excerpts por
execução, a projeção fica aproximadamente em 2,0 minutos para excerpts curtos,
5,2 minutos para 300 palavras ou 30,4 minutos para 800 palavras, sem contar
I/O, retries e concorrência com outros jobs.

## Como projetar custo

Para `N` excerpts e `C` chamadas por excerpt:

```text
segundos = N × C × latência_média_da_chamada
horas    = segundos / 3600
custo    = horas × custo_horário_da_máquina
```

O modelo é local; portanto, não há custo por token de API. O custo relevante é
CPU/GPU, memória, energia, hospedagem e tempo de operação. O benchmark mede
latência do processo já aquecido. A primeira requisição também é registrada
separadamente porque pode carregar o modelo.

O resultado não deve ser extrapolado para produção sem repetir a medição no
hardware de produção, com o número real de workers e a concorrência prevista.
O NLI permanece desligado por padrão (`ANALYTICS_NLI_ENABLED=false`) e limitado
por `ANALYTICS_NLI_MAX_EXCERPTS`; o benchmark não inicia jobs nem processamento
em massa.
