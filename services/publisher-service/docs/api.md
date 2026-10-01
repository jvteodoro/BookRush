# API de publisher

`POST /api/v1/publisher/submissions` cria uma submissão DRAFT associada ao subject autenticado. `GET /api/v1/publisher/submissions` lista apenas as submissões do mesmo publisher.
O título é obrigatório e limitado a 300 caracteres. O envio só aceita um
rascunho pertencente ao subject atual; uma segunda tentativa retorna 404 e não
altera uma submissão já enviada.
`GET /api/v1/publisher/submissions/{id}` consulta uma submissão somente quando
ela pertence ao subject autenticado. Upload de arquivo e métricas editoriais
continuam dependentes dos contratos de storage/behavior ainda em execução.
