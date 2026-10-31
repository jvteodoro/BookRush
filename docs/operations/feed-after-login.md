# Feed indisponível após login: capas e rota pública S3

O login pode concluir e ainda aparecer “Não conseguimos abrir seu feed” com
`NetworkError`. A inicialização busca capas autenticadas do catálogo, que
redirecionam para URLs assinadas do storage. Até esta correção, uma rejeição
nessa busca interrompia o `Promise.all` e todo o feed. Agora a capa indisponível
usa o fallback existente, mantendo a proteção da API e a restrição same-origin
na requisição inicial.

## Diagnóstico observado em 2026-10-31

A consulta pública a `https://seaweedfs-s3-bookrush.jteodoro.tec.br/` devolveu
exatamente o mesmo HTML do frontend de `https://bookrush.jteodoro.tec.br/`.
O preflight OPTIONS no domínio S3 respondeu 405 sem cabeçalhos CORS. Esse
resultado demonstra que o caminho público não está atendendo como a API S3.
Os logs anteriores também mostram OPTIONS de objetos recebidos pelo Nginx do
frontend. O erro exato da sessão deve ser conferido em Network no navegador;
a captura da tela, sozinha, não identifica qual requisição falhou.

## Correção no servidor

1. Confirme no arquivo protegido de ambiente que `STORAGE_PUBLIC_ENDPOINT`
   aponta para `https://seaweedfs-s3-bookrush.jteodoro.tec.br`, sem usar o host
   do frontend. Não copie esse arquivo ou suas credenciais para logs.
2. Confira DNS e o proxy TLS externo: preserve o Host e encaminhe o domínio
   S3 para o gateway Traefik, sem reescrevê-lo para o host da aplicação.
3. Confira o arquivo realmente montado no reverse proxy:

   ```bash
   docker inspect bookrush-reverse-proxy-1 --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
   ```

   O arquivo montado em `/etc/traefik/dynamic.yaml` precisa conter o router
   `seaweedfs-s3`, com o Host acima e o serviço `http://seaweedfs:8333`, conforme
   `infrastructure/traefik/dynamic.yaml` desta revisão. O Jenkins usa um Compose
   estável em `/opt/bookrush/compose.yaml`; o bind mount pode apontar para outro
   checkout do host, diferente do workspace Jenkins. Atualize o arquivo correto.
4. A configuração versionada usa `providers.file.watch=false`. Depois de
   corrigir o arquivo montado, reinicie somente o reverse proxy em uma janela
   apropriada para recarregar as rotas. Preserve volumes e os containers de
   Keycloak, PostgreSQL e SeaweedFS. Se o endpoint do catálogo mudar, aplique
   a nova configuração pelo deploy normal.
5. Confira CORS no endpoint S3 para a origem exata do frontend e os métodos e
   headers usados pela requisição. Não desative autenticação nem torne o bucket
   público para contornar a falha. Uma URL assinada nova deve responder com os
   bytes da imagem e o preflight deve ser aceito, sem HTML da SPA.

## Validação

Depois de publicar a correção do frontend e corrigir o roteamento, faça login
com uma sessão nova. Em Network, confira o feed, o redirect da thumbnail e a
resposta final da imagem. Verifique também o fallback bloqueando uma requisição
de capa: o feed deve abrir mesmo assim. Testes do frontend cobrem falha de rede,
HTTP 404, falha ao ler o blob e sucesso via cliente autenticado.

O sucesso do Jenkins e os healthchecks não validam esse caminho de navegador;
é necessário testar a origem pública e a sessão real.
