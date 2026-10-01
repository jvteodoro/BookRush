# Cliente BookRush

O cliente web do BookRush é uma SPA React responsiva inspirada na linguagem visual validada no protótipo PageLoop. O protótipo foi usado como referência de experiência; o cliente usa o catálogo e a autenticação reais do BookRush.

## Capacidades atuais

- login OIDC com Keycloak no realm `bookrush`, usando Authorization Code + PKCE;
- catálogo paginado em `GET /api/internal/v1/catalog/books`;
- busca por título;
- detalhes de um livro;
- estante local para salvar livros neste dispositivo;
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

## Mobile

O aplicativo React Native/Expo está em [`mobile/`](../../mobile/README.md). Ele compartilha o contrato do catálogo, usa o client público `bookrush-mobile` e pode gerar APK com `npx expo run:android` ou EAS. Os tokens também ficam somente na memória.

## Limitações intencionais

Feed social, comentários, progresso de leitura e recomendações dependem de APIs de produto que ainda não existem. A interface mostra o catálogo canônico sem inventar métricas editoriais ou dados sociais.
