# API de publisher

`POST /api/v1/publisher/submissions` cria uma submissão DRAFT associada ao subject autenticado. `GET /api/v1/publisher/submissions` lista apenas as submissões do mesmo publisher.
O título é obrigatório e limitado a 300 caracteres. O envio exige uma submissão `UPLOADED` pertencente ao subject atual, upload
`FINALIZED` e membership `ACTIVE`. Repetições em `SUBMITTED`/`PROCESSING` retornam
o estado atual sem repetir ingestão. Rascunhos sem arquivo confirmado recebem
409; uma submissão de outra conta recebe 404.

Com `PUBLISHER_STORAGE_ENABLED=true`, `POST /api/v1/publisher/submissions/{id}/upload`
cria uma intenção de upload no bucket privado de staging e devolve uma URL PUT
temporária. O cliente envia o arquivo diretamente ao storage e chama
`POST .../upload/{uploadId}/finalize` com o SHA-256. O serviço confirma o
`HEAD` do objeto antes de marcar o upload como `FINALIZED` e a submissão como
`UPLOADED`; nenhuma URL permanente é persistida.
`GET /api/v1/publisher/submissions/{id}` consulta uma submissão somente quando
ela pertence ao subject autenticado. Upload de arquivo e métricas editoriais
continuam dependentes dos contratos de storage/behavior ainda em execução.

O staging permanece opt-in: habilite `PUBLISHER_STORAGE_ENABLED=true` somente quando `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` e o bucket configurado estiverem disponíveis. Sem essas variáveis o serviço continua operacional para metadata, mas não registra intenções de upload.
O cliente S3 usa acesso path-style para SeaweedFS; isso evita resolução virtual-host
de `bucket.seaweedfs` dentro da rede Compose.

Quando o upload finalizado é submetido para revisão, o publisher chama o
`book-ingestion-service` pela rede interna. O ingestion baixa o objeto do bucket
privado de staging, verifica o SHA-256, cria a referência canônica `ADMIN_UPLOAD`,
envia o asset SOURCE ao catálogo e confirma o `catalogBookId` pelo callback
idempotente. O fluxo não publica o binário nem expõe o bucket.

Uma submissão pode receber `catalogBookId` quando a canonização confirmar o
livro. `GET /api/v1/publisher/submissions/{id}/metrics` consulta os agregados
diários do behavior-service por uma credencial S2S própria
(`bookrush-publisher-behavior-s2s`) com audience `bookrush-behavior` e scope
dedicado `bookrush.publisher.metrics.read`. O publisher confere a propriedade da submissão pelo
`subject_key` antes da chamada; a API interna do behavior retorna dados diários
e variação em janelas de 30 dias, sem identidades de leitores. Os dados de um
dia e a comparação entre períodos são suprimidos quando a amostra tem menos de
cinco leitores únicos com leituras. Totais de cabeçalho ficam indisponíveis se
a série contém dias suprimidos, evitando subtotais enganosos. Sem vínculo
canônico, retorna `NOT_LINKED`.

Configure `PUBLISHER_BEHAVIOR_S2S_CLIENT_SECRET` no ambiente protegido e
reconcilie o realm `bookrush-platform` para provisionar o cliente S2S. O segredo
não é necessário para o healthcheck, mas métricas de obras ligadas falham
fechadas enquanto ele não estiver configurado.

## Vínculo automático após ingestão

O endpoint interno `POST /api/internal/v1/publisher/submissions/{id}/catalog-link`
é chamado pelo `book-ingestion-service` depois que o comando canônico retorna o
`bookId`. Ele exige `X-BookRush-Ingestion-Token`, configurado somente nos
containers por `PUBLISHER_INGESTION_CALLBACK_TOKEN`, e é idempotente: repetir o
mesmo vínculo preserva o estado; tentar trocar o livro retorna conflito.
Uma execução de ingestão pode carregar `publisherSubmissionId` nos parâmetros
do comando. A submissão passa a `PROCESSING` e as consultas expõem também
`ingestion_job_id` e `linked_at`.

## Membership e revisão

O primeiro uso de uma submissão cria uma membership `ACTIVE` idempotente para
o publicador. Memberships `SUSPENDED` e `REVOKED` não podem criar ou alterar
submissões. `GET /api/v1/publisher/memberships` lista memberships do subject.

`PUT /api/v1/publisher/submissions/{id}/metadata` atualiza metadados do
rascunho e `POST /api/v1/publisher/submissions/{id}/rights` registra o estado
de direitos antes do envio. Operadores consultam `GET
/api/v1/publisher/review` e decidem uma submissão com `POST
/api/v1/publisher/submissions/{id}/review`, usando `APPROVED`, `REJECTED` ou
`CHANGES_REQUESTED`. Aprovação cria o registro de `published_work`.

`GET /api/v1/publisher/submissions/{id}/reviews` lista o histórico de decisões
do próprio publicador. Depois de publicada, `POST
/api/v1/publisher/submissions/{id}/withdraw` revoga a publicação do mesmo
subject, marca `published_work` como `WITHDRAWN` e emite um evento de domínio.
As rotas administrativas de revisão continuam restritas aos grupos de
operadores e usam CORS explícito.
Authentication uses the `bookrush-platform` issuer/JWK set; Keycloak groups
are mapped to `ROLE_*` authorities for operator-only workflow actions.

## Estatísticas dos meus livros

A console oferece `/admin/publisher`, usando a sessão do realm `bookrush-platform` (audience `bookrush-ingestion`). O menu Publicador do leitor aponta para essa página; deve ser usada a conta que publicou os livros.

`GET /api/v1/publisher/submissions/{id}/metrics?days=30` aceita 1–365 dias, incluindo hoje. Verifica `subject_key` e o vínculo aprovado em `published_work.publisher_subject_key`; um catalogBookId informado pelo usuário no rascunho nunca concede acesso. Outra conta recebe 404. Sem publicação confirmada retorna NOT_LINKED; sem atividade retorna AVAILABLE com items vazio; indisponibilidade retorna 503.

A interface mostra impressões, aberturas, leituras (READ_PROGRESS e BOOK_COMPLETE, não leitores únicos), curtidas, compartilhamentos e comentários, com totais e linhas diárias. Os agregados são atualizados periodicamente pelo behavior-service. Não são dados em tempo real nem um relatório de usuários identificáveis.

O publisher consulta `/api/internal/v1/publisher/metrics/books/{bookId}` por uma identidade S2S exclusiva: `bookrush-publisher-metrics-s2s`, realm plataforma, audience `bookrush-behavior`, scope `bookrush.publisher.metrics.read`. O bearer humano não é encaminhado. O token é cacheado até perto da expiração; timeout/falha de autenticação não vira atividade zero.

Para implantação, reconciliar o realm declarativo e configurar `PUBLISHER_METRICS_CLIENT_SECRET` de forma protegida, além de `PUBLISHER_METRICS_CLIENT_ID` e `PUBLISHER_METRICS_TOKEN_URI` se houver overrides. Sem essa credencial a interface indica indisponibilidade. Não inserir valores em Git. A rota pública existente é preservada pelo router publisher de `infrastructure/traefik/dynamic.yaml`; a rota interna não recebe router público próprio. CORS mantém as origens explícitas existentes da console.

Nenhuma migration foi necessária: reutilizamos published_work e behavior.daily_book. Deploy, reconcile no ambiente real e smoke público com sessão real precisam ser executados no ambiente de destino antes de declarar validação em produção.

## Envio de livros próprios pela console

`/admin/publisher` oferece criação de rascunho, retomada após recarregar a página,
seleção de EPUB/PDF, upload privado e envio para revisão. Arquivos vazios e
extensões incompatíveis são recusados antes de reservar o upload. O MIME é
normalizado pela extensão, inclusive quando o browser não informa `File.type`.
O cliente calcula SHA-256, envia o arquivo pela URL assinada **sem bearer humano**
e confirma o upload pela API autenticada. Não envia o livro para revisão antes
da confirmação do arquivo. Uma falha mantém o rascunho e permite nova tentativa.

A API restringe reserva/finalização aos rascunhos editáveis do dono com membership
ativa; valida MIME, SHA-256 e presença de bytes no objeto. Um envio que falha na
ingestão restaura `UPLOADED` somente se o callback ainda não avançou o estado.
O sucesso registra uma revisão `PENDING`; decisões posteriores a retiram da fila.
O status `PROCESSING` significa processamento/revisão, **não publicação aprovada**.
A aprovação editorial e a ativação do catálogo continuam nos fluxos administrativos.

Habilite `PUBLISHER_STORAGE_ENABLED=true` no ambiente com S3 disponível; configure
`STORAGE_ENDPOINT`, `STORAGE_PUBLIC_ENDPOINT` acessível ao navegador,
`STORAGE_BUCKET_STAGING=books-staging`, credenciais de storage e
`PUBLISHER_INGESTION_CALLBACK_TOKEN` compartilhado com ingestion. O handoff usa o bucket de staging configurado.
O bucket precisa aceitar PUT da origem explícita da console via CORS.
O serviço permanece opt-in quando o storage não está configurado. O controller de
upload é habilitado pela mesma propriedade do storage, sem depender da ordem de
registro dos beans Spring.

Não há migration nova. As rotas usam o gateway existente `/api/v1/publisher/**`,
realm `bookrush-platform`, audience `bookrush-ingestion` e dono por subject;
revisão editorial exige grupo `operators` ou `platform-admins`. A origem e os
headers permanecem na configuração CORS existente. A validação pública com sessão
real e o deploy da branch são necessários para confirmar funcionamento em produção.
