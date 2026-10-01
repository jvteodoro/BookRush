# API de publisher

`POST /api/v1/publisher/submissions` cria uma submissão DRAFT associada ao subject autenticado. `GET /api/v1/publisher/submissions` lista apenas as submissões do mesmo publisher.
O título é obrigatório e limitado a 300 caracteres. O envio só aceita um
rascunho pertencente ao subject atual; uma segunda tentativa retorna 404 e não
altera uma submissão já enviada.

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
