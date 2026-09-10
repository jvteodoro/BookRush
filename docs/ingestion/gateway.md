# Gateway de APIs

O Compose executa Traefik como gateway em `127.0.0.1:18081`. O Nginx do host termina TLS e encaminha o domínio inteiro para essa porta; ele não precisa conhecer cada microserviço.

O gateway usa o provider de arquivo (`infrastructure/traefik/dynamic.yaml`). Isso
mantém o roteamento explícito e evita que nomes, labels ou mudanças de estado do
Docker alterem as rotas administrativas. Os serviços continuam acessíveis entre
si pela rede `bookrush`:

| Prefixo | Serviço | Regra |
|---|---|---|
| `/` | `frontend:8080` | fallback de menor prioridade |
| `/api` | `catalog-service:8080` | catálogo e assets |
| `/ingestion` | `book-ingestion-service:8090` | prefixo removido antes do serviço |
| `/ingestion-docs/*` | `book-ingestion-service:8090` | prefixo preservado; corresponde ao `springdoc` do serviço |
| `/swagger-ui/*`, `/v3/api-docs` | `catalog-service:8080` | documentação do catálogo |

Ao adicionar um microserviço, publique apenas a porta interna na rede Docker e
acrescente uma rota e um serviço no provider de arquivo. Não publique a porta do
novo serviço no host. O socket Docker não é necessário; o dashboard do Traefik
permanece desativado.

Para aplicar a entrada do host, use `infrastructure/nginx/bookrush.conf.example`, apontando o `proxy_pass` para `http://127.0.0.1:18081`. Depois valide `nginx -t` e recarregue o serviço. O TLS continua sendo responsabilidade do Nginx externo.
