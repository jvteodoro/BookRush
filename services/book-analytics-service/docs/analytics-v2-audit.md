# Auditoria de baseline para Analytics V2

**Data:** 30 de setembro de 2026  
**Escopo:** comparação entre o checkout executável e a especificação Content
Analytics / Excerpt Candidate V2. Este documento é um inventário técnico; não
declara GO para processamento em lote nem validação científica.

## Invariantes confirmadas

- `book-analytics-service` é o único writer do schema `analytics`.
- A raiz de linhagem continua `catalog.book_asset_version`; excerpts mantêm
  hash, versão física e offsets Unicode em code points com intervalo
  half-open `[start, end)`.
- Flyway do serviço está em **V6**. As migrations V1–V6 permanecem imutáveis;
  a primeira migration V2 poderá ser V7 e será somente aditiva.
- Jobs, itens e `analysis_run` já são persistentes. A chave de operação e o
  hash de requisição preservam replay idempotente; cancelamento é cooperativo.
- O modo offline está configurado e os adaptadores de embedding, emoção e
  teacher label atualmente seguros retornam indisponibilidade, sem baixar
  modelos no startup.

## Matriz de estado

| Área V2 | Estado no checkout | Lacuna confirmada | Bead V2 |
| --- | --- | --- | --- |
| Artefatos offline | Há `model_artifact`, cache configurável e script de preparação; registry V1 ainda não fixa todos os checksums/revisões necessários. | Congelar seleção, licença, revisão e SHA-256 verificável. | `bookrush-av2-020` |
| Geração de candidatos | `ExcerptCandidateGenerator` V1 cria janelas por sentença, com offsets code point, hash e filtro básico de boilerplate. | Não suporta tamanhos múltiplos, limites/configuração V2, capítulo real, integridade de borda ou registro de todas as rejeições. | `bookrush-av2-030` |
| Definições Tier 0 | V3 registra cinco features de excerpt: `word_count`, `sentence_count`, `question_ratio`, `dialogue_ratio` e `estimated_read_time`. | Catálogo V2 requer definições adicionais de estrutura, ritmo e posição, com semântica/versionamento explícitos. | `bookrush-av2-040` |
| Cálculo determinístico | `TextFeatureCalculator` já calcula mais sinais que os cinco persistidos, incluindo parágrafos, pontuação, exclamações e posições. | Variância, CV, delta, burst, elipse/travessão e convenções V2 não estão implementados/testados como contrato persistido. | `bookrush-av2-050` |
| Corpus e linguística | Estruturas V1 existem para corpus frequency, diversidade lexical e readability. | Não há corpus PT/EN V2, bridge spaCy executável nem métricas linguísticas persistidas por V2. | `bookrush-av2-060` a `bookrush-av2-100` |
| Embeddings e semântica | Tabelas de embeddings de excerpt/capítulo/documento e aritmética vetorial existem; provider padrão é desabilitado. | BGE-M3 offline, contexto, protótipos e suas observações ainda não executam inferência real. | `bookrush-av2-110` a `bookrush-av2-130` |
| Inferência narrativa | Há contratos NLI/emoção e providers desabilitados. | Falta protocolo de validação V2 e inferência protegida por feature flag; nenhum constructo deve virar feature `VALID` por heurística. | `bookrush-av2-140`, `bookrush-av2-150` |
| Anotação e scorer | `excerpt_rank` armazena ranking explicável, mas não personalização. | Não há contrato H/C/A/I/Q, dataset, treino reproduzível, split seguro ou modelo supervisionado. | `bookrush-av2-160` a `bookrush-av2-190` |
| Diversidade, operação e decisão | Pruning V1 elimina vazio, tamanho e duplicata; métricas e E2E V1 existem. | Faltam gates de elegibilidade V2, MMR, benchmark V2 e decisão GO/NO-GO. | `bookrush-av2-200` a `bookrush-av2-220` |
| Contratos e documentação | OpenAPI, TechDocs e relatório V1 existem. | Atualizar somente após as capacidades V2 correspondentes estarem entregues. | `bookrush-av2-230` |

## Evidência de baseline

O comando abaixo foi executado antes da primeira alteração V2 e concluiu sem
falhas:

```bash
mvn -q -f services/book-analytics-service/pom.xml test
```

O primeiro uso no sandbox falhou apenas porque ele não pode escrever em
`~/.m2`; a repetição no ambiente do host eliminou essa limitação e passou.

Também foram inspecionados os seis scripts de migration, o worker persistente,
os geradores/pruners, a calculadora determinística, o contrato OpenAPI e os
testes unitários existentes. O relatório operacional do estado V1 continua em
`docs/analytics/report.md` na raiz do repositório.

## Grafo de execução

O arquivo de plano V2 já estava importado, porém os vínculos `depends_on` e
`parent` não haviam sido materializados no banco Beads. Foram recriadas as 37
dependências `blocks` e as 23 relações `parent-child` exatamente como descritas
no pacote, e `bd dep cycles --json` retornou uma lista vazia. Assim, a ordem de
implementação não depende de memória do agente: a auditoria bloqueia os beads
de geração/definições, e eles bloqueiam o Tier 0.

## Próximo incremento

Depois desta auditoria, os próximos beads desbloqueados são:

1. `bookrush-av2-020` — congelar artefatos e execução offline;
2. `bookrush-av2-030` — geração V2 multi-size;
3. `bookrush-av2-040` — definições determinísticas V2.

Eles podem evoluir em paralelo conceitual, mas a persistência de métricas Tier
0 só começa após gerador e definições, conforme o grafo Beads.

## Incremento Tier 0 entregue

O worker agora cria novas execuções `deterministic-text-v2` com a configuração
canônica `sentence-window-v2`. Ela gera janelas de 90, 140 e 200 palavras,
com strides 1, 2 e 3, mínimo de 60 palavras, máximo de 250 e sem atravessar
capítulos por padrão. A configuração está em `application.yml`, pode ser
ajustada por variáveis `ANALYTICS_V2_*`, é serializada no `analysis_run` e
recebe SHA-256 em `configuration_hash`.

Quando capítulos de primeiro nível existem no catálogo, o worker respeita seus
intervalos e persiste `chapter_id`; quando não existem, processa o documento
como uma única seção. As observações Tier 0 incluem estrutura, ritmo,
pontuação, elipse, travessão e posições relativas. `short_sentence_*` usa o
limiar versionado de oito palavras. Os resultados são measurements
reproduzíveis, e não scores de modelo ou produto.

## Artefatos de modelo congelados

O lock `config/model-artifacts-v1.json` fixa a seleção V1 de fastText, spaCy
PT/EN, BGE-M3 e mDeBERTa NLI. Para cada arquivo ele registra licença, fonte,
commit imutável quando aplicável e SHA-256. O BGE-M3 usa
`5617a9f61b028005a4858fdac845db406aefb181`; o NLI usa
`8adb042d524ecd5c26d3e3ba0e3fbcf7e2d0864c`. O registry e o lock são
validados juntos por `scripts/validate-analytics-specs.py`.

`make analytics-models-fetch` prepara o cache fora do runtime e só aceita os
arquivos que passam a verificação. `make analytics-models-verify` é seguro em
hosts isolados e não usa rede. O container monta o cache em modo somente
leitura, e a ausência de artefato continua sendo representada como
`MODEL_UNAVAILABLE`; não vira zero e não inicia download automático.

## Revalidação operacional posterior

Após a auditoria inicial, o runtime Python passou a usar fastText local para
validação de idioma e o worker passou a persistir embeddings de excerpt,
capítulo e documento por chunks, além de scores de protótipos e NLI narrativo e
emocional. Uma execução controlada real terminou com `2/2` itens bem-sucedidos,
1.600 observações NLI, 120 scores de protótipos e um embedding de documento.
Também foram registrados um modelo corpus-frequency EN e uma normalização de
estilo EN no schema `analytics`.

As lacunas que continuam verdadeiras são deliberadas: não existe ainda um
snapshot PT elegível, o classificador supervisionado ainda não tem dataset
avaliado nem artefato aceito, e os gates de validação humana/GO-NO-GO e o bulk
de 500–1000 livros ainda não foram executados.

### Piloto de corpus português

Em 30/09/2026 foi executada a publicação explícita de um snapshot PT de dois
textos Gutenberg, sem depender de API por livro. O objeto foi verificado no
bucket privado `books-ml` e a linha foi inserida no schema `analytics`:
`gutenberg-pt-pilot-20260930`, 162.419 tokens, 19.746 termos, SHA-256
`e2bd9fc35ee1aa25f7468603a4c2e8a39750cfef014bf868b095b533f73f577c`.
O resultado valida o caminho de aquisição e persistência, mas não fecha a
validação linguística PT nem autoriza bulk.
