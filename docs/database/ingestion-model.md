# Ingestão e proveniência (V5–V7)

source → ingestion_job → ingestion_item → book/edition; cada item pode apontar
para source_record, e cada versão física pode apontar para o item que a produziu.
Assim é possível descobrir o payload recebido e a tentativa concreta, não só
a biblioteca de origem. Não existe crawler implementado nesta etapa.

source_record preserva metadata JSONB em objeto (não texto integral ou binário),
external_id, hash SHA-256 lowercase e retrieved_at. V8 separa hash bruto e
semântico, revision/lastModified e locator RAW; cada observação de snapshot fica
em `source_record_observation`. Um hash diferente cria nova revisão; conteúdo
idêntico para a mesma fonte/ID reutiliza o registro. O serviço
de ingestão deve definir a serialização canônica usada no hash e limitar payloads;
a migration não calcula o hash nem limita bytes de JSON. Payloads grandes devem
ir para S3. Preservação sem alterações é contrato do serviço, não trigger SQL.

Jobs aceitam PENDING, RUNNING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED,
CANCELLED, CANCEL_REQUESTED e PAUSED, com trigger MANUAL, SCHEDULED ou RETRY.
V7 adiciona parâmetros/fingerprint/requester e `ingestion_job_attempt`, ligado
ao JobRepository do Spring Batch. Contadores são de **itens lógicos** na nova
pipeline; contagens de tentativas permanecem no histórico V5:
discovered inclui tentativas agendadas, processed = succeeded + failed e não
pode superar discovered. Cancelados não contam como processados. O coordenador
atualiza contadores atomicamente junto aos itens; não existem triggers agregadores.
Ainda não há máquina de transições ou exigência de horário para cada estado.

`ingestion_logical_item` possui uma chave tipada única por job. `ingestion_item`
continua armazenando as tentativas físicas históricas e aponta para o item lógico;
seu status permanece PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED, com número de
tentativa positivo. Retry preserva a linha anterior e cria nova tentativa no
mesmo item lógico, ou novo job RETRY. Pode falhar antes de resolver
obra ou edição. FK composta impede edição de outra obra e impede registro
original de outra fonte/ID. Se finished_at existe, started_at deve existir e
não ser posterior. Erros não devem conter credenciais nem payload completo.

`ingestion_task` é a fila durável. Cada operação possui `operation_key` única,
`next_attempt_at`, contador, lease, heartbeat e `fence_token`; uma tentativa
fica em `ingestion_task_attempt`. Claims futuros usarão relógio do banco e
`SKIP LOCKED`, e o serviço deverá comparar o fence no commit para impedir que
um worker antigo finalize depois da expiração da lease.

Exemplo: um job Gutenberg agenda 1342 e 11; 1342 resolve Pride and Prejudice,
edição inglesa, source_record e versão EPUB; 11 falha sem book_id. Uma terceira
tentativa para 11 pode ser registrada com attempt_number=2. Os contadores devem
refletir três tentativas, e os erros anteriores permanecem consultáveis.

A FK ingestion_item_id da versão é opcional para upload administrativo e
artefatos internos. Ela garante existência; coerência entre a obra do item e
a do asset deve ser verificada pelo coordenador (transformações podem produzir
outros artefatos). A origem bibliográfica pode ser consultada pelos itens ligados
à obra/edição. `field_provenance` registra a decisão por campo e impede que um
valor antigo seja silenciosamente sobrescrito por uma fonte de menor precedência.

Todos os vínculos históricos usam RESTRICT. Nenhum dado literário é seed de
produção. Fixtures locais e consultas estão em queries.sql e testes.
