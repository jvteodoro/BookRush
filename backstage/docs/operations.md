# Operação e troubleshooting

## Persistência, backup e restore

Banco do portal é separado. Faça pg_dump do backstage-postgres e guarde o arquivo fora
do Git. Restaure somente em banco vazio isolado e teste login/tasks antes de trocar tráfego.
O catálogo pode ser reconstruído dos Locations Git, mas sessões e tasks não.
TechDocs local tem volume; produção usa bucket S3 dedicado com backup/versionamento.
A origem autoral sempre está no Git; regenerar documentos não muda as fontes.

## Diagnóstico

| Sintoma | Diagnóstico | Correção |
|---|---|---|
| Catálogo vazio | logs de processing, Location path/URL | corrigir URL e credencial; aguardar refresh |
| Owner desconhecido | validar refs | registrar Group, não inventar pessoa |
| TechDocs 404 | annotation, mkdocs.yml e publisher | gerar/publicar entidade com namespace/kind/name corretos |
| Build docs falha | log MkDocs ou Mermaid | corrigir link/diagrama; instalar Chromium e ferramentas fixadas |
| GitHub 403 | escopo e repo do token | aplicar privilégio mínimo, sem imprimir token |
| OIDC login negado | issuer, redirect URI, User email | corrigir cadastro e URI exata; manter resolver restritivo |
| Mixed content | app/backend baseUrl e proxy | usar mesma URL HTTPS no perfil público |
| PostgreSQL indisponível | compose ps/logs e volume | corrigir conexão; não apagar volume |
| Template falha | task log, repositório e branch | conferir escrita Contents/PR e nome exclusivo |

Revisões entram por Git; atualize imagem/config e reinicie somente o portal se necessário.
As aplicações funcionam mesmo com o portal desligado. Para dezenas de serviços, monitore
fila de processamento do catálogo, latência PostgreSQL, erros TechDocs e espaço do builder.
