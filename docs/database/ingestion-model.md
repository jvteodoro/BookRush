# Ingestão e proveniência (V5)

source → ingestion_job → ingestion_item → book/edition; cada item pode apontar
para source_record, e cada versão física pode apontar para o item que a produziu.
Assim é possível descobrir o payload recebido e a tentativa concreta, não só
a biblioteca de origem. Não existe crawler implementado nesta etapa.

source_record preserva metadata JSONB em objeto (não texto integral ou binário),
external_id, hash SHA-256 lowercase e retrieved_at. Um hash diferente cria nova
revisão; conteúdo idêntico para a mesma fonte/ID reutiliza o registro. O serviço
de ingestão deve definir a serialização canônica usada no hash e limitar payloads;
a migration não calcula o hash nem limita bytes de JSON. Payloads grandes devem
ir para S3. Preservação sem alterações é contrato do serviço, não trigger SQL.

Jobs aceitam PENDING, RUNNING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED e CANCELLED,
com trigger MANUAL, SCHEDULED ou RETRY. Contadores são de **tentativas**:
discovered inclui tentativas agendadas, processed = succeeded + failed e não
pode superar discovered. Cancelados não contam como processados. O coordenador
atualiza contadores atomicamente junto aos itens; não existem triggers agregadores.
Ainda não há máquina de transições ou exigência de horário para cada estado.

Um item possui status PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED, número de tentativa
positivo e unicidade job/ID externo/tentativa. Retry preserva a linha anterior e
cria nova tentativa no mesmo job, ou novo job RETRY. Pode falhar antes de resolver
obra ou edição. FK composta impede edição de outra obra e impede registro
original de outra fonte/ID. Se finished_at existe, started_at deve existir e
não ser posterior. Erros não devem conter credenciais nem payload completo.

Exemplo: um job Gutenberg agenda 1342 e 11; 1342 resolve Pride and Prejudice,
edição inglesa, source_record e versão EPUB; 11 falha sem book_id. Uma terceira
tentativa para 11 pode ser registrada com attempt_number=2. Os contadores devem
refletir três tentativas, e os erros anteriores permanecem consultáveis.

A FK ingestion_item_id da versão é opcional para upload administrativo e
artefatos internos. Ela garante existência; coerência entre a obra do item e
a do asset deve ser verificada pelo coordenador (transformações podem produzir
outros artefatos). A origem bibliográfica pode ser consultada pelos itens ligados
à obra/edição; provenance por campo e resolução de conflitos serão evoluções.

Todos os vínculos históricos usam RESTRICT. Nenhum dado literário é seed de
produção. Fixtures locais e consultas estão em queries.sql e testes.
