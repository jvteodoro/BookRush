# BookRush — concluir a importação Gutenberg de ponta a ponta

> Prompt de implementação para o agente que trabalha no repositório BookRush.
> Elaborado a partir do `docs.zip` fornecido e do diagnóstico do agente em 09/09/2026.
> Esta análise examinou documentação, não o código nem o ambiente em execução. Afirmações sobre implementação devem ser verificadas no repositório.

## 1. Missão e condição de encerramento

Continue a implementação existente até ser possível **importar livros automaticamente pela API administrativa**, com metadados canônicos, arquivos persistidos, processamento e resultado verificável.

O diagnóstico atual é que `POST /api/admin/v1/ingestion/run` apenas muda o estado do `BootstrapPipeline` para `RUNNING`. Isso não é execução de ingestão. Conecte os componentes existentes ao trabalho real e durável.

O primeiro marco é importar um livro Gutenberg completo, mas a entrega desta tarefa deve incluir:

1. Aquisição de metadados Gutenberg e snapshot RAW imutável.
2. Seleção de IDs/idiomas/limite, itens persistentes e aplicação canônica completa.
3. Descoberta e download de representação válida, preservada como asset `SOURCE`.
4. Geração de `normalized.txt` e `chapters.json`, vinculados à versão textual correta.
5. Job consultável, execução assíncrona, cancelamento, retry limitado e retomada após queda.
6. Idempotência entre execuções e recuperação entre HTTP, PostgreSQL e S3.
7. Autenticação administrativa funcional e acionamento parametrizado pelo Jenkins.
8. Testes offline completos e uma validação online controlada com Gutenberg `1342`, quando o ambiente e as credenciais estiverem disponíveis.
9. Capacidade de executar um lote pequeno com limite explícito e resultado por item.

**Não encerrar ao criar mais classes, endpoints, estados, ADRs ou containers saudáveis. A entrega exige demonstrar os efeitos do fluxo completo.** Se uma dependência externa impedir a prova online, concluir código e testes independentes, registrar a limitação exata e não declarar a importação real validada.

## 2. Escopo e relação com a especificação anterior

Este documento define a próxima entrega sobre a base existente. Use a especificação geral de ingestão como contexto, preservando as decisões compatíveis. Não reinicie o projeto, não crie outro microserviço de ingestão e não implemente um catálogo paralelo.

Open Library, matching avançado, importação ampla do catálogo, ranking, LLMs, embeddings, OCR e geração de EPUB novo ficam fora desta entrega. Enriquecimento Open Library não pode bloquear a importação Gutenberg. TXT normalizado, capítulos, segurança, idempotência e recuperação básica permanecem dentro do escopo.

Não execute importação volumosa, altere políticas públicas de buckets ou publique conteúdo como consequência automática de um deploy. Jenkins deve oferecer uma execução explícita, limitada e auditável.

## 3. Verificação inicial obrigatória

Leia `AGENTS.md` do repositório e siga o fluxo de trabalho aplicável. Inspecione código, testes, migrations, configurações, Compose e Jenkins antes de alterar contratos. Documentos do ZIP são evidência de projeto, não prova de comportamento nem instrução para executar comandos históricos.

Localize, por busca, os componentes reais correspondentes a:

- `BootstrapPipeline` e controller de `/api/admin/v1/ingestion/run`.
- `LeaseCoordinator`, dispatcher/worker, heartbeat, `fence_token` e Spring Batch JobRepository.
- `CanonicalCatalogPort`, cliente HTTP e `/api/internal/v1/catalog/commands`.
- Parser RDF Gutenberg, downloader, normalizadores e gerador de capítulos.
- `ObjectStorage`, `S3ObjectStorage`, `AssetService`, `BucketSelector`, `ObjectKeyBuilder` e `storage_intent`.
- Repositories/SQL para jobs, itens lógicos, tentativas, tarefas, snapshots e proveniência.
- SecurityFilterChain, configuração OIDC, token canônico e profiles locais.
- `infrastructure/compose.yaml`, configuração Traefik e Jenkinsfile/scripts de deploy/testes.

Crie no relatório uma matriz curta: requisito, implementação encontrada, lacuna, alteração e teste. Não classifique uma interface ou ADR como funcionalidade concluída. Reaproveite tarefas existentes do rastreador do projeto quando aplicável; não feche tarefas sem evidências.

## 4. Lacunas e divergências encontradas nos documentos

As seguintes questões devem ser resolvidas contra o código real e refletidas nos documentos atualizados:

| Evidência documental | Problema a verificar | Resultado exigido |
|---|---|---|
| `docs/ingestion/canonical-api.md` descreve criação/atualização de obra e identificador | Contrato publicado não demonstra criação de edição, autores, créditos e proveniência | Comando canônico completo, transacional e retornando todos os IDs necessários |
| `docs/database/entities.md` exige Gutenberg ID associado à edição | API resumida menciona obra/identificador, sem edição | Gutenberg ID deve resolver a edição digital e sua obra, sem violar a FK/tipo |
| `docs/storage/buckets.md` separa RAW de SOURCE | Diagnóstico sugere ebook canônico em `books-raw` | Catálogos/snapshots em RAW; EPUB/TXT original associado ao catálogo em `books-source`, respeitando configuração |
| `docs/storage/architecture.md` descreve DELETE compensatório após falha | `docs/ingestion/adr/005-s3-postgres-idempotency.md` exige recuperar intent sem apagar upload verificado | Fluxo de ingestão recuperável, sem DELETE cego após perda de resposta/commit |
| `docs/storage/object-key-convention.md` proíbe nova versão SOURCE | ADR-005 exige bytes alterados produzirem versão imutável | Definir e implementar versionamento SOURCE no fluxo interno, preservando compatibilidade da API legada |
| `docs/database/ingestion-model.md` alterna contagem lógica e de tentativas | Contadores podem inflar em retries | Contrato único: livros/itens lógicos separados de tentativas físicas |
| Documentos descrevem ownership canônico e operacional em tabelas preexistentes | Não está demonstrado como comandos validam lease remota | Ownership e validação de fencing especificados e testados na fronteira que efetivamente grava |
| `docs/ingestion/gateway.md` fala em labels Docker; estudo de incidentes registra provider de arquivo | Roteamento documentado pode estar desatualizado | Verificar configuração real e publicar rota externa correta sem recriar o incidente |
| Relato informa `INGESTION_SECURITY_ENABLED=false` e admin negado | Serviço saudável não significa API operacional | Fluxo autenticado real para operador/Jenkins; não abrir `permitAll` em produção |

Não editar migrations já aplicadas ou executar `flyway repair` para fazer testes passarem. O estudo de incidentes é histórico. Corrigir schema por nova migration aditiva, quando necessário, preservando volumes e histórico.

## 5. Arquitetura que deve permanecer

```text
Operador/Jenkins autenticado
  -> API de ingestão
  -> job e tarefas duráveis
  -> worker real
       -> aquisição Gutenberg -> RAW privado
       -> parser/seleção -> source_record e itens
       -> comandos HTTP autenticados -> catalog-service -> PostgreSQL
       -> download validado -> storage intent -> S3 books-source
       -> confirmação canônica -> asset/version disponível
       -> processamento -> S3 books-processing
       -> confirmação derivados/capítulos/linhagem
  -> consulta do job por ID e relatório de execução
```

`catalog-service` permanece proprietário das entidades canônicas e de suas migrations. O serviço de ingestão coordena o trabalho. Não escrever diretamente em `book`, `edition` ou assets para contornar um endpoint incompleto.

A existência de tabelas operacionais no schema `catalog` não autoriza dois donos de migrations. Registre quais tabelas a ingestão pode operar pelo datasource atual e quais dependem de comandos do catálogo. Reutilize o modelo `ingestion_logical_item` + `ingestion_item` de tentativas; não crie tabelas `*_v2`.

Chamadas HTTP e S3 ficam fora de transações longas. Há consistência recuperável entre serviços, sem promessa de transação distribuída.

## 6. Tornar a API um disparador real

### 6.1 Criação e consulta

Evolua a rota existente com compatibilidade quando viável:

```http
POST /api/admin/v1/ingestion/run
Authorization: Bearer <token de operador>
Idempotency-Key: gutenberg-1342-import-001
Content-Type: application/json

{
  "source": "GUTENBERG",
  "externalIds": ["1342"],
  "languages": ["en"],
  "maxItems": 1,
  "dryRun": false,
  "processAssets": true
}
```

Este é um contrato-alvo, não uma alegação sobre o controller atual. Ajuste o binding e publique exemplos executáveis reais. Use representação de idioma consistente com o catálogo, normalizando `en/eng` sem trocar campos de significado diferente. Não aceite URL arbitrária, SQL ou path do servidor no corpo.

Responder `202 Accepted`, `jobId`, estado inicial e `Location` para consulta por ID. Persistir parâmetros e seleção antes de despachar. Idempotency-Key com mesmo corpo retorna o mesmo job; corpo diferente retorna `409`. O worker deve buscar trabalho persistido mesmo depois de reiniciar.

Fornecer consulta e cancelamento por job, por exemplo:

```text
GET  /api/admin/v1/ingestion/jobs/{jobId}
GET  /api/admin/v1/ingestion/jobs/{jobId}/items
POST /api/admin/v1/ingestion/jobs/{jobId}/cancel
POST /api/admin/v1/ingestion/jobs/{jobId}/resume
```

Se manter `/state` e `/cancel` legados, documentar exatamente a qual job se referem. Um singleton de estado em memória não pode ser a autoridade de múltiplas execuções. Listagens devem ser paginadas.

`INGESTION_SCHEDULER_ENABLED=false` deve desligar o agendamento periódico de novas importações, **não impedir o dispatcher de executar jobs explicitamente submetidos**. Separar essas configurações caso hoje estejam acopladas.

### 6.2 Dry run

Dry run deve adquirir ou reutilizar o snapshot permitido, interpretar a fonte, aplicar filtros, verificar metadados e descobrir candidatos de asset. Produzir plano com IDs selecionados, formatos, política, exclusões e pendências.

Pode persistir job, auditoria e snapshot/staging, mas não criar entidades canônicas ou assets, não baixar ebooks e não marcar uma importação como concluída. Direitos/credenciais ausentes aparecem como bloqueios no plano. Na execução real, preferir reutilizar o snapshot e a seleção congelados pelo plano; se houver mudança, informar antes de executar.

## 7. Aquisição Gutenberg e seleção

Implemente um job que:

1. Resolva uma fonte machine-readable configurada e permitida.
2. Baixe em streaming com limites, timeout, User-Agent identificável e validação de redirects/host.
3. Finalize o temporário e calcule SHA-256 dos bytes recebidos.
4. Grave o snapshot privado em RAW e registre manifest/localizador, tamanho, validators, fonte e versão do parser.
5. Parseie RDF com XXE/DTD externo e acessos de rede desabilitados.
6. Selecione apenas os IDs/idiomas/tipos solicitados e persista itens com razão de seleção/exclusão.
7. Registre a revisão de `source_record` recuperável e a observação do snapshot pelo mecanismo proprietário existente.

Para a prova de um livro, aceitar RDF individual obtido pelo canal machine-readable/mirror autorizado, ou catálogo completo previamente adquirido com seleção de um registro. Escolher a opção comprovada no código/configuração e registrar a origem real. Não exigir download integral de um dump em cada teste online.

URLs de ebooks vêm do RDF ou de índice autorizado, não de concatenação de padrões supostos. Não raspar páginas humanas. Os canais oficiais e políticas devem ser revalidados na implementação.

Não criar obra fictícia para guardar RAW. Não carregar arquivo completo em memória. Se houver retomada parcial, validar Range/ETag/Last-Modified e reiniciar quando não for possível garantir os mesmos bytes.

Para IDs explícitos, um ID não encontrado deve gerar resultado visível `NOT_FOUND/EXCLUDED`, conforme contrato, sem terminar silenciosamente com zero itens e status de importação bem-sucedida.

## 8. Completar os comandos canônicos

### 8.1 Comando bibliográfico

Evolua `/api/internal/v1/catalog/commands` ou introduza operações tipadas compatíveis, com validação e resposta persistida. Aplique em uma transação local no catálogo:

- Fonte existente e registro de origem/observação quando sob seu ownership.
- Obra, com título e dados comprovados.
- Edição digital Gutenberg associada à obra.
- Contribuidores e créditos de obra/edição nos papéis corretos.
- Identificador Gutenberg associado à edição digital, conforme modelo existente.
- Informação de licença disponível e proveniência; ausência de evidência permanece desconhecida.
- Relação entre item de ingestão e IDs canônicos quando essa escrita couber ao catálogo.

Não copiar a data de disponibilização do ebook para `first_publication_year` ou ano de edição impressa. Não unir autores apenas por nome; usar identificador confiável quando suportado ou vínculo de origem determinístico. Nomes e títulos não são chaves únicas.

Se o schema não suportar ID Gutenberg de autor, use o mecanismo de vínculo de fonte existente ou uma evolução aditiva explícita. Não grave o ID de autor com `identifier_type=GUTENBERG_ID` reservado à edição para contornar constraints.

Resposta mínima: `commandId`, `operationKey`, resultado `CREATED/UPDATED/NOOP/CONFLICT`, `bookId`, `editionId`, IDs de contribuidores/vínculos relevantes e revisão aplicada.

### 8.2 Idempotência do comando

Persistir resposta na mesma transação do efeito canônico. Disponibilizar consulta autenticada por operation key/command ID para recuperação de resposta perdida. Não gerar nova operação só porque ocorreu timeout.

Definir o hash do **conteúdo de negócio imutável**: tipo/versão do comando, fonte, identidade do registro e payload. Excluir fence token, trace ID e número de tentativa desse hash; retries com nova lease devem reenviar o mesmo comando de negócio sem criar falso conflito. O catálogo deve recalcular/verificar o hash, não confiar cegamente no valor enviado.

Operation keys não incluem job ID quando a operação precisa deduplicar entre execuções. Mudança real de payload/regra produz nova identidade de revisão/operação. Chave igual e payload divergente retorna conflito; não sobrescrever silenciosamente o resultado antigo.

### 8.3 Fencing efetivo entre serviços

Enviar `fenceToken` não basta. Comparar somente com o maior token já observado também não prova que a lease ainda é válida: um worker expirado pode chegar antes do worker novo.

Inspecione onde vive a autoridade de `ingestion_task`. Se catálogo e coordenador compartilham essa tabela, faça a confirmação canônica bloquear/validar a tarefa corrente na mesma transação da escrita, verificando task ID, owner, token e lease vigente pelo relógio do banco. O claim de sucessor deve se serializar contra essa validação.

Se a autoridade estiver em outro banco, implemente um protocolo explícito que registre no catálogo a autorização/fence antes de permitir efeitos e serialize revogação/confirmacão, ou redefina a garantia para idempotência mais conclusão local protegida e documente/teste a limitação. **Não afirmar rejeição de toda escrita de worker expirado com um simples high-water mark.** A solução adotada deve impedir alteração conflitante e finalização indevida, inclusive na corrida antes da chegada do sucessor.

## 9. Assets e protocolo de confirmação

### 9.1 Seleção e localização

Preferir EPUB válido; fallback HTML/TXT conforme a política implementada. Para uma primeira execução, baixar apenas a representação principal, evitando multiplicação de formatos. EPUB fonte deve ficar como `asset_type=EPUB`, `asset_role=SOURCE`, ligado à edição/obra corretas.

Respeite os buckets existentes:

- `books-raw`: catálogo/RDF/datasets/manifests antes da canonização.
- `books-source`: ebook original associado ao catálogo.
- `books-processing`: texto normalizado, capítulos e derivados privados.

Os nomes vêm de configuração. Não duplicar o EPUB em RAW e SOURCE sem razão de retenção explícita. Não realocar objetos antigos indiscriminadamente. Preservar `ObjectKeyBuilder` e compatibilidade de keys já emitidas; nova convenção só para novos objetos, documentada e testada.

### 9.2 Fluxo implementável obrigatório

1. Reivindicar a tarefa com lease/fence e obter metadados canônicos.
2. Baixar em arquivo temporário limitado, calcular hash/tamanho e validar assinatura, MIME e estrutura.
3. Solicitar ao catálogo uma reserva idempotente de asset/versão e `storage_intent`, retornando localização imutável e IDs. Essa reserva fica pendente; não marca disponibilidade.
4. Fazer PUT condicional na localização reservada usando o adaptador S3 existente, fora da transação.
5. Em conflito de objeto já existente, verificar bytes/localização/intenção antes de reutilizar; nunca apagá-lo como compensação automática.
6. Confirmar presença, tamanho e SHA-256. HEAD/metadata e ETag não provam o hash real sozinhos; o fluxo deve usar checksum confiável validado pelo servidor ou GET com cálculo de hash. A prova end-to-end usa GET.
7. Enviar comando idempotente de finalização; catálogo confere reserva, autorização de operação e evidência de verificação antes de marcar a versão disponível/corrente elegível.
8. Confirmar tarefa local com lease/fence e persistir resultado. Se a resposta HTTP se perder, consultar/repetir o mesmo comando.

O catálogo não deve aceitar um booleano arbitrário `verified=true` como única prova de integridade. Deve verificar via storage ou consumir evidência vinculada ao intent por um protocolo interno autenticado e testado. URLs recebidas do cliente não definem destino de verificação.

Não transportar o livro em JSON/base64. A escolha de upload direto interno ou envio por stream ao serviço de storage deve reutilizar o caminho existente mais completo, mantendo intent, idempotência e confirmação. Implemente um caminho real, não duas alternativas incompletas.

### 9.3 Versões SOURCE e falhas

Repetir os mesmos bytes para o mesmo asset lógico retorna NOOP, sem nova versão. Bytes diferentes da mesma representação Gutenberg devem permitir nova versão SOURCE interna, com novo objeto e histórico preservado. Diferenciar representações legítimas para não colapsar assets distintos por tipo/papel.

Se a API administrativa legada proíbe versões SOURCE, mantenha sua compatibilidade e ofereça uma operação interna restrita de atualização de origem, ou evolua o contrato conscientemente. Não criar um asset novo em todo retry para contornar a proibição.

Falha após PUT/antes da finalização deixa intent recuperável. Não fazer DELETE imediato de objeto verificado por timeout ou falha de commit. A reconciliação deve finalizar, reagendar ou classificar pendência; limpeza física de órfãos só com período de segurança e confirmação de ausência de referências/trabalho ativo.

Uma nova versão pendente não substitui versão válida corrente. Falha de normalização não invalida EPUB original saudável.

## 10. Conectar normalização, capítulos e linhagem

Após SOURCE disponível, crie tarefa de processamento com operation key baseada em versão/hash de entrada, processador/versão e configuração. Conecte os parsers já existentes em vez de criar uma segunda implementação.

Resultado obrigatório:

```text
SOURCE EPUB/HTML/TXT
  -> normalized.txt: UTF-8, NFC, LF
  -> chapters.json: estrutura e offsets sobre o TXT final
  -> asset_processing + processing_input/processing_output
  -> book_chapter, conforme modelo existente
```

Guardar derivados como assets versionados em `books-processing`. O JSON deve referenciar `textAssetVersionId` e SHA-256 da versão exata. Gere/reserve o ID da versão textual antes de serializar o JSON para não introduzir referência fictícia ou circular.

Offsets são code points Unicode, intervalo `[start,end)`, calculados depois da normalização. Testar emoji, acentos e caracteres combinantes. Sem estrutura segura, usar um único capítulo `DOCUMENT/FALLBACK`; nunca inventar capítulos sem evidência.

Preservar original. Não remover licença/atribuição indiscriminadamente. Não armazenar texto completo em PostgreSQL. Processamento tem limites de tempo/memória/expansão; EPUB/HTML não executam scripts ou acessam rede. Gerar os mesmos bytes para mesma entrada/configuração/versão; timestamps de execução ficam fora dos derivados determinísticos.

O item só conclui quando as etapas solicitadas concluírem. Se `processAssets=false` for suportado, o resultado deve dizer explicitamente “original importado, processamento não solicitado”; o teste principal desta tarefa usa `true`.

## 11. Jobs, recuperação e contadores

Use a fila e coordenação existentes como única autoridade. O dispatcher deve executar tarefas elegíveis, persistir dependências e renovar heartbeat; não encadear todo o fluxo apenas em callbacks de memória.

Estados devem refletir trabalho observável:

- PENDING: aceito e persistido.
- RUNNING: worker está executando etapas, com tentativas e progresso.
- COMPLETED: todos os itens selecionados terminaram o escopo solicitado.
- COMPLETED_WITH_ERRORS: itens concluíram com falhas/quarentenas toleradas, discriminadas.
- FAILED: falha fatal ou nenhum item solicitado pôde ser importado por erro.
- CANCEL_REQUESTED/CANCELLED: cancelamento cooperativo preservando resultados.
- PAUSED: condição recuperável explicitada, sem anunciar sucesso.

Dry run e seleção vazia precisam de resultado próprio no relatório, mesmo se reutilizarem estados terminais existentes. Não alegar “livro importado” para eles.

Métricas mínimas: itens descobertos/selecionados, concluídos, NOOP, excluídos, falhos, pendentes e tentativas. Se duas tentativas forem necessárias para um livro, contar um item lógico e duas tentativas. Ajustar documentação e constraints legadas de forma compatível, sem reescrever histórico indiscriminadamente.

Retry mínimo: timeout/408/429/5xx com orçamento limitado, jitter e `Retry-After`; 401/403 pausam para correção; erro de payload/segurança vai para falha/quarentena. Persistir próxima tentativa; não ocupar worker dormindo por minutos. Harmonizar retries HTTP/SDK com retries do job para evitar multiplicação.

Resume reusa seleção/snapshot e operações existentes. Cancelar não apaga arquivos válidos. Depois de queda, retomar tarefas expiradas e resultados pendentes sem intervenção SQL manual.

Reconciliação mínima automatizada desta entrega cobre intents pendentes, resposta canônica perdida, lease expirada e versão registrada sem objeto. Distinguir ausência `404/NoSuchKey` de timeout/403. Remover órfãos fisicamente pode permanecer operação manual protegida; identificar e relatar pendências não pode.

## 12. Autenticação e gateway

Valide o comportamento real de `INGESTION_SECURITY_ENABLED` antes de corrigir. Não interpretar `false` como autorização para liberar admin em produção.

Entregar:

1. OIDC com assinatura, issuer, audience, expiração e roles validados, usando propriedades reais.
2. Operador para iniciar/cancelar/retomar, leitor para consultar, revisor para direitos; conforme regras existentes.
3. Credencial service-to-service para catálogo (`CANONICAL_SERVICE_TOKEN` ou mecanismo vigente), distinta de `ASSET_ADMIN`.
4. Harness local de autenticação para fixtures, sem bypass que possa ser ativado acidentalmente em produção.
5. Testes de token inválido, papel insuficiente e chamada autenticada bem-sucedida.

Não inventar issuer, client ID, audience, segredo ou JWKS. Se não estiverem disponíveis, preparar configuração validada, exemplos e teste local, e listar exatamente o provisionamento externo necessário. Nunca imprimir tokens ou `.env` no relatório/Jenkins.

Verifique rotas interna e externa. Os documentos indicam remoção de prefixo `/ingestion` no gateway: a chamada externa pode ser `/ingestion/api/admin/v1/ingestion/run`, mas deve ser validada contra a configuração real. Não usar a rota Swagger como rota de execução nem alterar Nginx/Traefik sem necessidade.

## 13. Jenkins: executar e acompanhar uma importação

Adicione pipeline/job ou estágio **opt-in** separado do deploy rotineiro, reutilizando convenções do repositório. Parâmetros mínimos:

```text
RUN_INGESTION=false por padrão
SOURCE=GUTENBERG
GUTENBERG_IDS=1342
LANGUAGES=en
MAX_ITEMS=1
DRY_RUN=true por padrão
PROCESS_ASSETS=true
WAIT_TIMEOUT_MINUTES=<limite explícito>
```

Valide parâmetros sem interpolar entrada livre em shell. Identifique ambiente e commit/imagem testados. Não fazer ingestão automática em qualquer push ou recriar volumes para preparar o teste.

Fluxo do job:

1. Verificar saúde e configuração operacional.
2. Obter token de máquina pelo mecanismo configurado e guardar só em memória/segredo Jenkins.
3. Executar dry run e arquivar plano sanitizado.
4. Quando a execução real estiver explicitamente selecionada, submeter importação usando snapshot/seleção do plano e chave estável para retries da mesma submissão.
5. Consultar job por ID até terminal, com polling limitado/backoff e timeout total.
6. Em timeout, registrar job ainda pendente e link/ID; não alegar cancelamento remoto se não foi confirmado.
7. Arquivar relatório JSON, contagens, IDs, hashes e falhas sem credenciais.
8. Para o teste de aceitação, considerar falha do Jenkins qualquer estado diferente de conclusão esperada de todos os itens; não tratar `COMPLETED_WITH_ERRORS` como sucesso completo.

Polling nunca deve chamar `/run` novamente para consultar progresso. Se o build reiniciar após submissão incerta, recuperar pela chave/ID em vez de criar outro job acidentalmente.

## 14. Testes que provam conclusão

### 14.1 Fluxo offline reproduzível

Criar/estender harness com PostgreSQL, SeaweedFS e os dois serviços reais em containers; autenticação de teste controlada e servidor HTTP de fixtures. Não substituir a API canônica por mock na prova end-to-end.

Corpus sintético pequeno com RDF e EPUB/HTML/TXT conhecidos. A fonte fixture usa allowlist isolada; exceção para rede local de teste não pode permitir SSRF em produção. CI padrão não depende de Gutenberg online.

Executar pela API pública administrativa do serviço, não por chamada direta a método interno ou inserts manuais de livro. Dados de referência mínimos são permitidos; a obra/edição/assets devem ser criados pelo pipeline.

### 14.2 Matriz obrigatória

| Teste | Evidência exigida |
|---|---|
| Run real de fixture | Retorna job e cria registros, bytes, derivados e linhagem |
| Segunda execução da mesma fixture | Mesmos IDs canônicos e versões; resultado NOOP quando cabível |
| Resposta perdida após commit canônico | Recupera comando, sem entidade duplicada |
| Queda após PUT/antes de finalizar | Recupera intent, sem apagar objeto válido nem criar outra versão |
| Lease expirada | Worker obsoleto não conclui; testar corrida antes da chegada do sucessor |
| Nova revisão bibliográfica | Atualiza somente campos elegíveis e mantém proveniência |
| Bytes novos para mesmo SOURCE | Nova versão, preservando antiga e controle da corrente |
| Nova versão do processador | Novos derivados sem novo download do original |
| MIME inválido/HTML de erro como EPUB | Rejeição; nenhuma versão AVAILABLE indevida |
| XXE/ZIP bomb/redirect proibido | Sem rede/acesso indevido; erro controlado |
| Dry run | Nenhuma obra/edição/asset criado; plano rastreável |
| 429/403 | Retry programado/pausa corretos e limitados |
| Cancelar/retomar | Resultados confirmados preservados, tarefas pendentes recuperadas |
| Lote pequeno, uma falha | Resultado individual correto e contadores sem inflar |
| Direitos desconhecidos | Nenhuma distribuição pública |
| Autenticação real | Admin correto funciona; sem token/papel incorreto não funciona |

Evite testes que apenas afirmam `state == RUNNING`. A evidência deve verificar efeitos no PostgreSQL e bytes lidos do S3.

### 14.3 Prova online controlada: Gutenberg 1342

Após testes offline, executar uma importação limitada usando metadados/arquivo reais adquiridos por canal permitido. Revalidar URL, formato e direitos; não transformar fixture em “teste real”.

Esperado bibliográfico a conferir na fonte: *Pride and Prejudice*, Jane Austen, edição digital Gutenberg em inglês. Preservar forma de nome/título da origem; não hardcodificar esses valores no parser ou no comando de produção.

Registrar:

- Job ID, horários, revisão de código/imagem e ambiente.
- URL de origem sanitizada, snapshot ID/hash e tamanho.
- `source_record`, book, edition, contributor, crédito e identificador Gutenberg.
- Asset SOURCE/version ID, bucket/key, SHA-256 local e SHA-256 obtido por GET do S3.
- IDs/hashes de TXT e JSON, capítulos e linhagem.
- Estado terminal e contadores.
- Segunda execução com comparativo de contagens/IDs provando ausência de duplicação.

Reexecutar prova de interrupção em ambiente isolado de teste, não derrubar serviço compartilhado de produção para demonstrar recovery. A importação online fica privada; não implica autorização de publicação.

Se o ambiente online estiver bloqueado por credenciais, rede ou política, relatar **“fluxo offline validado; prova online pendente por X”**, acompanhada do comando/runbook pronto. Não fechar a pendência de importação real com apenas healthchecks.

## 15. Ordem de trabalho e critérios por marco

### Marco A — um original importado

Ligar API → job durável → aquisição RDF → registro/itens → comando canônico completo → download → reserva/upload/confirmação de SOURCE. Encerrar este marco somente após verificar banco e GET de bytes. Continuar para os demais marcos dentro da mesma tarefa.

### Marco B — importação recuperável

Validar idempotência entre execuções, timeout de comando, falha após PUT, fences, retry limitado e retomada. Corrigir versionamento SOURCE e compensação antiga onde afetarem o fluxo.

### Marco C — processamento e lote

Conectar TXT/capítulos/linhagem, cancelar/retomar e executar lote pequeno. Preservar limites e resultados por item.

### Marco D — operação efetiva

Entregar autenticação, gateway correto, Jenkins opt-in, harness end-to-end e runbook. Executar prova online quando houver condições e registrar limitações reais.

Não promover o primeiro marco a conclusão de toda a tarefa. Não adiar idempotência básica para “depois que estiver funcionando”; ela faz parte do funcionamento.

## 16. Documentação a atualizar

Atualize os documentos existentes para refletir o comportamento implementado:

- `docs/ingestion/README.md`: o que realmente funciona e caminho de execução.
- `docs/ingestion/canonical-api.md`: comandos completos, resposta, consulta, hash e fencing.
- `docs/ingestion/openapi.md`: criação/consulta/cancelamento por job, corpo real e erros.
- `docs/ingestion/operations.md`: executar, repetir, retomar e reconciliar.
- `docs/ingestion/security-and-rights.md`: provisionamento e uso seguro do operador/Jenkins.
- `docs/ingestion/gateway.md`: configuração e URLs verificadas.
- `docs/database/ingestion-model.md`: item lógico, tentativas, contadores e ownership.
- `docs/storage/architecture.md`, `buckets.md`, `object-key-convention.md` e `operations.md`: protocolo novo e diferenças da API legada.
- ADRs afetados: registrar decisão final e testes, evitando documentos que se contradizem.

Adicione `docs/ingestion/gutenberg-import-runbook.md` e `docs/ingestion/gutenberg-e2e-report.md`. O runbook precisa de comandos executáveis com placeholders apenas para segredos/configuração externa; deve explicar de onde vem cada job/snapshot ID. Não criar exemplos de endpoint inexistente.

## 17. Definition of Done específica

- [ ] `/run` realmente enfileira e executa trabalho persistente.
- [ ] Scheduler periódico desligado não impede job manual.
- [ ] Consulta por job informa etapas, itens, resultado e erros reais.
- [ ] Fonte Gutenberg gera snapshot RAW e registro rastreável.
- [ ] Catálogo recebe obra, edição, autores, créditos e identificadores corretos.
- [ ] EPUB/HTML/TXT original chega ao bucket SOURCE e tem hash confirmado por GET.
- [ ] TXT e capítulos estão persistidos, versionados e ligados à entrada.
- [ ] Segunda execução não duplica entidades/assets/versões.
- [ ] Alteração real de SOURCE cria versão nova sem destruir a anterior.
- [ ] Falhas entre serviços/storage são recuperáveis sem SQL manual no fluxo normal.
- [ ] Worker obsoleto não produz conclusão indevida; garantia de fencing está demonstrada.
- [ ] Dry run não simula importação concluída.
- [ ] OIDC/Jenkins conseguem chamar API com credenciais corretas; testes negativos passam.
- [ ] Pipeline Jenkins acompanha o job e arquiva relatório sem tokens.
- [ ] Harness offline completo passa com serviços reais.
- [ ] Prova online de um livro foi executada, ou está explicitamente pendente por dependência externa identificada.
- [ ] Documentação não confunde buckets, contadores, APIs legadas e fluxo novo.
- [ ] Migrations antigas e volumes persistentes foram preservados.

## 18. Resposta final obrigatória do agente

Entregue resumo direto e relatório com:

1. **Agora é possível importar?** Responder separadamente para fixtures offline, fonte real e Jenkins remoto.
2. **O que foi conectado/corrigido?** Componentes e arquivos principais.
3. **Como executar?** Comandos reais de dry run, importação, consulta, segunda execução e retomada.
4. **Prova do livro:** job ID, IDs canônicos, hashes, buckets, derivados e contagens antes/depois.
5. **Testes:** comandos executados, resultados e falhas injetadas; não reutilizar números de relatórios antigos.
6. **Segurança/configuração:** variáveis e credenciais ainda necessárias, sem valores secretos.
7. **Pendências:** distinguir requisito bloqueado de extensão futura como Open Library.

Não responder apenas “infraestrutura pronta” ou “pipeline em RUNNING”. Não declarar sucesso completo se a única evidência for build, Swagger ou healthcheck. O resultado esperado desta tarefa é uma importação observável, repetível e recuperável.
