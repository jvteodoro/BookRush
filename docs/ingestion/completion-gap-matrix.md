# Matriz de lacunas — conclusão da importação Gutenberg

Esta matriz confronta o `PROMPT_CONCLUSAO_INGESTAO_GUTENBERG_BOOKRUSH.md` com o
código existente. Interfaces, migrations e ADRs são tratados como contexto;
um item só será considerado concluído quando houver efeito observável no
PostgreSQL, no SeaweedFS e nos endpoints reais.

## Estado encontrado

| Requisito | Evidência no repositório | Lacuna atual | Alteração planejada | Evidência de conclusão |
|---|---|---|---|---|
| Disparar uma importação | `BootstrapPipeline` usa `AtomicReference`; `IngestionAdminController` chama apenas `start()` | Não existe job persistente, seleção, `202`, idempotência ou dispatcher | Evoluir API para criar `ingestion_job`, itens lógicos e tarefas | POST retorna `jobId`; consulta sobrevive restart |
| Fila e leases | V7 possui `ingestion_task`, tentativas, `fence_token`; `LeaseCoordinator` faz claim/heartbeat/complete | Nenhum worker executa a fila e não há validação canônica do fence | Conectar dispatcher e confirmação protegida pela mesma autoridade | Corrida com lease expirada é rejeitada |
| Aquisição Gutenberg | `GutenbergRdfParser` e `SnapshotDownloader` existem; fixtures RDF existem | Nenhum job baixa RDF, cria snapshot, registra `source_record` ou seleciona itens | Implementar aquisição limitada e rastreável | Snapshot hashado em `books-raw` e itens com resultado |
| Catálogo canônico | `CanonicalCatalogPort`, adapter HTTP e endpoint interno existem | Comando não demonstra edição, créditos, identificador Gutenberg e resposta recuperável completos | Evoluir comando transacional e idempotente | Retorno contém obra, edição, contribuintes e operação |
| Asset SOURCE | `S3RawObjectStore`, `ObjectStorage`, `storage_intent` V9 e `AssetService` existem | Não há encadeamento descoberta → reserva → PUT → GET → confirmação | Implementar job de asset com `books-source` e versões SOURCE | Hash local e hash por GET coincidem |
| Processamento | Normalizador, extratores, `ChapterStructureWriter` e V11 existem | `AssetNormalizeJob` só normaliza uma string; não cria assets, linhagem nem capítulos no fluxo | Conectar TXT, JSON, `processing_input/output` e `book_chapter` | Derivados versionados ligados à entrada exata |
| Recuperação | Políticas de retry, reconciliação e cleanup existem | São serviços isolados; não há retomada de jobs nem reconciliação automática de intents | Integrar dispatcher, retries persistidos e resume | Falhas pós-PUT e respostas perdidas recuperam sem duplicação |
| Segurança | OIDC condicional da ingestão e token canônico existem | Perfil de produção precisa de provisionamento e testes de operador; modo sem OIDC nega admin | Definir papéis e harness seguro | Token/papel correto funciona; inválidos falham |
| Gateway | Traefik file provider em `infrastructure/traefik/dynamic.yaml` | Rotas existem, mas prefixos administrativos precisam ser validados junto da API final | Testar rota externa e atualizar runbook | API, Swagger e health respondem pelo domínio correto |
| Jenkins | Pipeline de deploy existe | Não há estágio opt-in de importação, polling ou relatório | Adicionar execução Gutenberg limitada e auditável | Build não importa por padrão; execução explícita arquiva relatório |
| Prova | Fixtures e testes unitários/integração parciais existem | Não há harness end-to-end acionado pela API nem prova 1342 | Criar harness offline e depois prova online | IDs, hashes, contagens e segunda execução registrados |

## Contratos que orientam a implementação

O request de importação terá fonte, IDs externos, idiomas, limite, `dryRun` e
`processAssets`. A URL, SQL, path local ou bucket não serão aceitos no corpo.
`Idempotency-Key` identifica a submissão; mesma chave com corpo diferente é
conflito.

O fluxo usará os buckets assim:

```text
books-raw        RDF, snapshots e manifests antes da canonização
books-source     EPUB/HTML/TXT original associado à edição
books-processing normalized.txt, chapters.json e derivados privados
```

O `catalog-service` continua dono das entidades bibliográficas e da decisão de
disponibilidade. O serviço de ingestão coordena aquisição, tarefas e chamadas
autenticadas. Nenhuma migration já aplicada será editada; evoluções de schema
serão aditivas.

Uma execução real só será terminal quando seus efeitos forem verificáveis:
metadados canônicos no PostgreSQL, bytes lidos do S3, hashes confirmados,
derivados ligados por linhagem e contadores por item lógico separados das
tentativas físicas.

## Ordem de execução

1. Persistir job, seleção, idempotência e dispatcher.
2. Conectar aquisição Gutenberg e comando canônico.
3. Implementar reserva/upload/confirmação de SOURCE.
4. Conectar normalização, capítulos e linhagem.
5. Integrar recuperação, segurança, gateway e Jenkins.
6. Provar o fluxo em fixture offline e, somente depois, no ID Gutenberg 1342.

As migrations V1–V12 existentes foram apenas inspecionadas nesta etapa.
