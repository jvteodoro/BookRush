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

O build publicado usa `VITE_BASE_PATH=/admin/` por padrão e a pipeline também o
passa explicitamente. Sem esse valor, o
Vite gera referências como `/assets/index.js`; o gateway encaminha essa URL ao
portal principal, que devolve HTML no lugar de JavaScript e deixa o admin em
branco. A pipeline fixa esse caminho, desativa `VITE_USE_MOCKS` e injeta as
configurações públicas de Keycloak e das APIs antes de construir a imagem.

## Diagnóstico

- `401`: confira issuer, audience, roles e expiração do token no realm
  administrativo.
- `403`: a conta precisa da permissão administrativa correspondente no backend;
  o portal não eleva privilégios.
- erro de rede: valide as rotas `/api`, `/ingestion`, `/analytics` e `/feed` no
  gateway e o cabeçalho `Access-Control-Allow-Origin`.
- tela vazia após alteração de configuração: reconstrua a imagem, pois variáveis
  `VITE_*` são compiladas, e limpe o cache do navegador.
