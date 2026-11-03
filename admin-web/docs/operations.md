# Operação

## Configuração

Copie `.env.example` para `.env` no ambiente de desenvolvimento. Em produção,
os valores públicos do Vite são argumentos de build; nenhum segredo deve ser
colocado no bundle. O client `labsoft-admin-web` é público, usa Authorization
Code + PKCE S256 e pertence ao realm `bookrush-platform`.

## Build e execução

```bash
npm ci
npm run lint
npm run build
docker build -t bookrush/admin-web:local admin-web
docker compose -f infrastructure/compose.yaml --env-file .env up -d admin-web
```

O serviço escuta somente em `127.0.0.1:18084`. O gateway publica a console em
`https://bookrush.jteodoro.tec.br/admin/`, preservando a aplicação pública na
raiz; não exponha o container diretamente na Internet. O job Jenkins `bookrush-admin-web` executa
build, lint e imagem, e só faz deploy quando o parâmetro `DEPLOY` é marcado.

O Nginx da imagem encaminha `/api/v1/admin/` para o `admin-service` pela rede
interna do Compose. Assim, tanto o caminho `/admin/` no gateway quanto o host
administrativo dedicado mantêm a API na mesma origem do navegador, preservam o
bearer recebido e não dependem de CORS entre domínios.

O build publicado usa `VITE_BASE_PATH=/admin/` por padrão e a pipeline também o
passa explicitamente. Sem esse valor, o
Vite gera referências como `/assets/index.js`; o gateway encaminha essa URL ao
portal principal, que devolve HTML no lugar de JavaScript e deixa o admin em
branco. A pipeline fixa esse caminho, desativa `VITE_USE_MOCKS` e injeta as
configurações públicas de Keycloak e das APIs antes de construir a imagem.
Os defaults do `Dockerfile` também preenchem essas configurações: valores
vazios não podem chegar ao bundle, pois produziriam `/realms//` e
`client_id=` no redirecionamento OIDC.

## Diagnóstico

- `401`: confira issuer, audience, roles e expiração do token no realm
  administrativo.
- `403`: a conta precisa da permissão administrativa correspondente no backend;
  o portal não eleva privilégios.
- erro de rede: valide as rotas `/api`, `/ingestion`, `/analytics` e `/feed` no
  gateway e o cabeçalho `Access-Control-Allow-Origin`.
- tela vazia após alteração de configuração: reconstrua a imagem, pois variáveis
  `VITE_*` são compiladas, e limpe o cache do navegador.

## Comentários

A seção Comentários em Users & moderation permite buscar pelo texto, autor ou
ID do livro e filtrar por visibilidade. A listagem usa páginas de 25 registros.
Ocultar, remover e restaurar exigem confirmação com justificativa de 3 a 80
caracteres. Cancelar não envia uma decisão. Durante o envio as ações ficam
bloqueadas; falhas preservam a justificativa para nova tentativa. Após sucesso,
a listagem, os indicadores e o histórico são atualizados pelo servidor.

Histórico exibe as últimas 100 decisões com operador, data, motivo e transição
de visibilidade. Remover preserva o texto para auditoria e permite restaurar;
comentários ocultos/removidos deixam de aparecer aos leitores. O fluxo de
resolução de denúncias é independente da visibilidade do comentário.

A remoção pelo próprio autor aparece com data e indicador específico. As ações
de visibilidade ficam bloqueadas nesse caso; a API também impede restauração
com 409. O texto preservado continua disponível para análise de denúncias.
