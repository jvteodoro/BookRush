# social-service

Likes, follows, comments and reports. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Também são suportados follows e reports; reports entram como registros OPEN para moderação.

Migration V3 adiciona follows e reports, mantendo report como fila de moderação.

As mutações de like, follow, report, share e comentário também emitem fatos
server-side ao behavior-service. A entrega usa o bearer autenticado, chave
única por evento e três tentativas; falha de telemetria não desfaz a mutação
social já persistida.
