# Caracterização do runtime de analytics

## Objetivo

Este teste compara o tempo de inferência do runtime com CPU e GPU usando
excertos reais selecionados do lote de analytics em produção. A medição cobre
somente a chamada HTTP ao modelo, com o modelo aquecido; ela não representa o
tempo total de um item da pipeline, que inclui leitura do EPUB, seleção,
persistência, armazenamento, retries e coordenação do worker.

O teste é reproduzível com o script
`scripts/benchmark-analytics-runtime-characterization.py`.
O script recebe JSONL com `id` e `text`, evitando acoplamento a credenciais ou
ao banco de produção.

## Resultado de 2026-10-18

A amostra foi composta pelos 8 excertos mais longos disponíveis nos itens já
processados do job `d692b8ae-ae00-4b6c-b860-44574509c714`, com batch 4. Os dois
containers usaram o mesmo cache de modelos e a mesma carga.

| Operação | CPU por item | GPU por item | Ganho observado |
| --- | ---: | ---: | ---: |
| BGE-M3 embeddings | 1.250 ms | 185 ms | 6,8× |
| NLI batch | 1.040 ms | 113 ms | 9,2× |

Todas as requisições retornaram `VALID`. As medidas são wall time HTTP por
item, calculado a partir de duas requisições de quatro itens; por isso esta
amostra não deve ser usada como promessa de throughput para todos os livros.

## Relação com a pipeline

No mesmo banco, o job atual tinha 498 itens, 38 processados, 36 concluídos e 2
falhos no momento da consulta. A distribuição persistida para
`EXCERPT_EMBEDDINGS` concluída foi:

- média: 226.853 ms por item;
- p50: 224.247 ms;
- p95: 282.702 ms;
- máximo: 2.179.103 ms.

O valor da fase é muito maior que o tempo de uma chamada de modelo porque a
fase executa vários lotes, faz persistência idempotente dos vetores, registra
progresso e pode sofrer espera de banco/rede ou retries. Portanto o benchmark
indica o potencial de aceleração do modelo, mas não demonstra ainda o ganho
end-to-end da pipeline.

O job anterior (`8bd66272-6dfa-4546-8b1b-7e0d802b01df`) foi cancelado e deixou
255 itens falhos, além de uma falha de embeddings com 12.022.686 ms. Esses
dados não devem ser misturados ao p95 de itens concluídos do job atual.

## Próxima medição recomendada

Para fechar a caracterização do bead `bookrush-av201`, medir no worker CUDA
com uma amostra pequena e controlada:

1. tempo de inferência por lote;
2. tempo de persistência por lote;
3. tamanho do batch, throughput e p95;
4. memória de vídeo e comportamento quando o batch excede a VRAM;
5. equivalência CPU/GPU das probabilidades NLI e dos hashes/dimensões dos
   embeddings;
6. recuperação após reinício entre dois lotes.

Só depois desses dados o batch padrão deve ser aumentado no serviço de
produção. A GPU testada é uma GTX 1050 Ti com 4 GiB, portanto o limite seguro
deve ser definido pelo p95 de tamanho dos textos e pela VRAM disponível, não
apenas pelo número de itens.

## Sweep GPU adicional

Em 2026-10-18, o runtime CUDA foi medido novamente com os mesmos 8 excertos e
batch 1, 4 e 8. O healthcheck confirmou `cuda=true`, GTX 1050 Ti e 4.225.630.208
bytes de VRAM total. Batch 4 manteve todas as respostas `VALID`, NLI com
equivalência absoluta máxima `0.0` e embeddings com diferença máxima `6e-8`
contra o batch 1. Batch 8 de NLI falhou com `CUDA out of memory` (a GPU ficou
com aproximadamente 71 MiB livres); por isso o default de NLI permanece 4/8
conforme o serviço, e batch 8 não é considerado seguro nesta GPU.

O planner Java de retomada possui teste unitário que verifica que uma tentativa
subsequente solicita somente índices sem vetor persistido, mantendo a ordem
original. A prova de reinício do worker real ainda deve ocorrer no próximo
deploy controlado; o job produtivo não foi interrompido para produzir essa
evidência.
