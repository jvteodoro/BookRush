# Cliente BookRush

O cliente web do BookRush é uma SPA React responsiva que carrega o shell original do protótipo PageLoop depois de autenticar. Assim, sidebar, feed, recomendações, busca, biblioteca, leitor, perfil e navegação mobile mantêm a experiência de referência, enquanto livros, feed, impressões e ações de biblioteca usam as APIs reais do BookRush.

## Capacidades atuais

- login OIDC com Keycloak no realm `bookrush`, usando Authorization Code + PKCE;
- catálogo paginado em `GET /api/v1/books`;
- busca por título;
- detalhes de um livro;
- biblioteca persistida por `reader-state-service`;
- abertura idempotente de livro e eventos de comportamento autenticados;
- layout responsivo para desktop e telas pequenas;
- estados explícitos de carregamento, catálogo vazio, sessão expirada e erro da API.

A API continua sendo a fronteira de autorização. O navegador não persiste access tokens; a implementação usa memória e estado transitório de protocolo.

## Desenvolvimento

```bash
npm install
npm run dev
npm run build
```

A configuração OIDC pode ser sobrescrita com `VITE_OIDC_AUTHORITY` e `VITE_OIDC_CLIENT_ID`. O cliente padrão é `bookrush-web`, provisionado em `infrastructure/keycloak/bookrush-realm.json`.

O shell visual é [`public/pageloop.js`](../public/pageloop.js) e seu CSS de
referência. `src/main.tsx` faz a ponte autenticada: injeta os livros retornados
por `GET /api/v1/books` sem Bearer (leitura pública), carrega a biblioteca em
`/api/v1/reader/library` e fornece as
ações de salvar/abrir para o shell. O cliente não usa livros fictícios como
fallback e não usa `localStorage` como fonte de dados; estado transitório da
interface permanece em memória e dados de produto ficam no backend.
O feed usa `GET /api/v1/reader/feed` e registra viewability em
`POST /api/v1/recommendations/impressions/{id}/viewable` antes de abrir um
livro.
O Nginx do container envia `Cache-Control: no-store` para que uma publicação
não mantenha o bundle anterior no navegador.
Os assets do shell também recebem uma versão explícita na URL para invalidar
caches de borda durante uma publicação.

## Mobile

O aplicativo React Native/Expo está em [`mobile/`](../../mobile/README.md). Ele compartilha o contrato do catálogo, usa o client público `bookrush-mobile` e pode gerar APK com `npx expo run:android` ou EAS. Os tokens também ficam somente na memória.

## Limitações intencionais

Feed social, comentários, progresso de leitura e recomendações dependem de APIs de produto que ainda não existem. A interface mostra o catálogo canônico sem inventar métricas editoriais ou dados sociais.
