# Analytics V2 — implementação e operação

O serviço continua sendo o único dono do schema `analytics`. O worker Java coordena jobs e lineage; `book-analytics-runtime` é um worker Python interno, sem banco próprio, usado somente quando os artefatos locais estão preparados.

## O que está implementado

- candidatos V2 com offsets Unicode em code points, limites por capítulo, deduplicação e pruning auditável;
- métricas estruturais, diversidade lexical, legibilidade e status de amostra curta;
- contratos de features V2, protótipos semânticos versionados, lock de modelos e validação CI;
- bridge spaCy PT/EN local-only, com extração segura de wheels preparadas;
- endpoint BGE-M3 local-only com lote, hash SHA-256, pooling mean e normalização L2;
- gates técnicos, ranking experimental, protocolo NLI limitado e seleção MMR determinística;
- script de artefato de scorer com split por `work_id`, seed, padronização e hash;
- observabilidade de baixa cardinalidade, sem IDs de livros em labels.

## Preparação de modelos

Nenhum modelo é baixado no startup. Execute `make analytics-models-fetch` somente em uma máquina autorizada e depois `make analytics-models-verify`. O cache é montado como somente leitura no runtime. Sem o cache, as respostas são `MODEL_UNAVAILABLE`, nunca zero fabricado. As revisões, licenças e SHA-256 estão em `config/model-artifacts-v1.json`.

## Persistência e recomputação

As migrations V7–V9 são aditivas. Observações carregam analyzer/configuração/status e nunca sobrescrevem versões anteriores. Excerpts referenciam `book_asset_version`; novas versões de texto geram novos offsets e hashes. Embeddings só podem ser publicados quando o modelo registrado e o input hash coincidirem.

## Gates antes do corpus

Os testes locais cobrem fórmulas e invariantes, mas não constituem GO para o corpus. Ainda é necessário preparar artefatos reais, executar os gates de 10/50/100 livros e registrar throughput, storage, avaliação humana e decisão GO/NO-GO. O scorer permanece `EXPERIMENTAL`; não representa qualidade, engajamento ou probabilidade comportamental.
## Validação real no ambiente de produção

Em 2026-09-30 foi executada uma prova controlada com duas versões textuais
normalizadas já persistidas no catálogo: Gutenberg 1342 (Pride and Prejudice)
e Gutenberg 84 (Frankenstein). O job `60673d9d-73b5-44ee-a3c5-d0d7cce114af`
terminou com `2/2` itens bem-sucedidos e zero falhas.

Os modelos fastText, spaCy EN/PT, BGE-M3 e mDeBERTa foram baixados com
checksum, copiados para o volume Docker de modelos e executados em modo
offline. O runtime Python foi validado com respostas reais de linguagem,
embedding de 1024 dimensões e NLI.

O piloto limitado do segundo livro persistiu 10 excerpts, 400 observações de
features, 10 rankings e 10 embeddings BGE-M3 em `books-ml`, com hashes e
`input_asset_version_id`. O primeiro item persistiu 5.095 excerpts, 203.800
features e 5.095 rankings. Um backfill controlado adicional persistiu 10
embeddings BGE-M3 para Frankenstein. O limite temporário foi removido após a prova;
`ANALYTICS_MAX_EXCERPTS_PER_ITEM=0` é o comportamento normal.

O worker envia embeddings em lotes de quatro textos. Esse limite é necessário
para evitar timeouts de inferência BGE em CPU; o contrato continua idempotente
por hash do texto e chave do objeto.

Como controle de origem, os arquivos públicos `1342-0.txt` (738.046 bytes,
SHA-256 `81300b79e8a8d65ac530a97578417d06137e3bbc90622a10a65e5036183d2500`)
e `84-0.txt` (421.633 bytes, SHA-256
`06c37d2c52d208d3d81eb12c3b10b5edbd7728b73554325ddceadbe2fb427e77`) foram
baixados em `/tmp/bookrush-real-corpus-20260930`. Eles servem como evidência
reproduzível da fonte; os dados processados pelo job continuam sendo as
versões normalizadas privadas já registradas no catálogo/SeaweedFS.

## Fases avançadas executadas

Uma execução controlada posterior, limitada a dez excerpts por item, terminou
com `2/2` itens bem-sucedidos. Ela persistiu 1.600 observações NLI narrativas e
emocionais e 120 scores de protótipos semânticos. O fallback de documento sem
capítulos catalogados também foi validado e persistiu um `document_embedding`
BGE-M3 de 1.024 dimensões; embeddings de capítulo só são criados quando há
intervalos em `catalog.book_chapter`.

O fastText `lid.176.bin` foi conectado ao endpoint interno `/v1/language` e
retornou `en` com confiança `0,9477` em uma amostra. O snapshot Gutenberg piloto
em inglês gerou e publicou um artifact corpus-frequency privado com `202813`
tokens e `10761` termos; a normalização de estilo correspondente foi registrada
com checksum em `analytics.style_normalization_model`. Ainda não há snapshot
bibliográfico PT elegível nem classificador supervisionado aceito; ambos exigem
dataset e avaliação próprios antes de qualquer promoção.

### Atualização do piloto corpus PT (30/09/2026)

Foi publicado um snapshot offline controlado de dois textos portugueses do
Project Gutenberg (`gutenberg-55682.txt` e `gutenberg-69187.txt`). O artefato
privado foi gravado em `books-ml` com o snapshot
`gutenberg-pt-pilot-20260930`, tokenizer `unicode-word-nfc-lower-v1`, 162.419
tokens, 19.746 termos e SHA-256
`e2bd9fc35ee1aa25f7468603a4c2e8a39750cfef014bf868b095b533f73f577c`.
A linha correspondente foi registrada em `analytics.corpus_frequency_model`.

Esse snapshot é um piloto de validação do artefato, não autorização para
processar o corpus completo. Ainda falta uma amostra PT de livros elegíveis
com observações linguísticas produzidas pelo pipeline para publicar a
normalização de estilo PT. O classificador supervisionado também permanece
bloqueado até existir dataset rotulado suficiente, com divisão por obra e
avaliação reproduzível.
