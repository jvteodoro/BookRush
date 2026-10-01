# Cliente BookRush

O cliente web do BookRush é uma SPA React responsiva inspirada na linguagem visual validada no protótipo PageLoop. O protótipo foi usado como referência de experiência; o cliente usa o catálogo e a autenticação reais do BookRush.

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

O layout atual reproduz a linguagem visual do PageLoop diretamente em React
(capas, abas, busca, biblioteca e modal de leitura). Não carrega `pageloop.js`,
arrays de demonstração ou `localStorage` como fonte de dados: livros, biblioteca
e eventos vêm das APIs reais.

## Mobile

O aplicativo React Native/Expo está em [`mobile/`](../../mobile/README.md). Ele compartilha o contrato do catálogo, usa o client público `bookrush-mobile` e pode gerar APK com `npx expo run:android` ou EAS. Os tokens também ficam somente na memória.

## Limitações intencionais

Feed social, comentários, progresso de leitura e recomendações dependem de APIs de produto que ainda não existem. A interface mostra o catálogo canônico sem inventar métricas editoriais ou dados sociais.
