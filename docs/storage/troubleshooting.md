# Diagnóstico

| Sintoma | Causa provável | Diagnóstico | Correção |
|---|---|---|---|
| API não fica pronta | endpoint interno ou gateway indisponível | Compose ps; health/logs SeaweedFS | Usar http://seaweedfs:8333 no Docker; conferir rede |
| 403 S3 | credenciais divergentes | conferir origem da configuração sem imprimir valores | Recriar API/gateway com par consistente |
| Bucket inexistente | bootstrap não completou ou nome mudou | list-buckets autenticado; logs de startup | Corrigir nomes/permissões e reiniciar backend |
| SignatureDoesNotMatch | host/porta/path/relógio alterados | comparar endpoint público configurado e endereço acessado | Assinar no endpoint final; preservar Host no proxy |
| URL usa nome Docker | public-endpoint incorreto | inspecionar apenas host da URL, nunca log completo | Configurar hostname alcançável pelo leitor antes de assinar |
| Virtual host falha | path-style incompatível/DNS | conferir STORAGE_PATH_STYLE e DNS do bucket | SeaweedFS local usa true |
| Porta ocupada | outro serviço no loopback | docker compose ps; ss -lnt | Mudar BOOKRUSH_S3_PORT e endpoint público juntos |
| Filer não inicia | volume sem permissão ou disco cheio | logs e espaço do host | Corrigir ownership/armazenamento; não apagar volume para contornar |
| Objeto sem registro | carga fora da aplicação ou reserva ausente | inventário S3 comparado com banco | Investigar origem antes de quarentenar/remover manualmente |
| Registro sem objeto | remoção externa/restore incompleto | HEAD autenticado, distinguir 404 de 403/timeouts | Restaurar objeto ou marcar MISSING |
| URL expirada | TTL/relógio | conferir expiresAt e relógios | Solicitar URL nova e sincronizar relógio |
| Aprovação 409 | licença desconhecida, role não PUBLIC ou upload pendente | consultar asset/versões/licença | Revisar informação e concluir upload; não converter UNKNOWN em permissão |
| Browser fetch bloqueado | CORS não configurado | console, GET por navegação funciona | Usar navegação/download ou configurar CORS restrito em etapa própria |
| Jenkins recusa deploy | storage não provisionado | health do container bookrush-seaweedfs-1 | Procedimento inicial em operations.md |
