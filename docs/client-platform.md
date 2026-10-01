# Clientes BookRush

O cliente web em `frontend/` e o aplicativo React Native em `mobile/` são as interfaces do leitor. Ambos usam o catálogo canônico do `catalog-service`; não duplicam livros nem criam um banco local de domínio.

## Web

O Vite gera uma SPA responsiva servida pelo Nginx. O login usa `bookrush-web` no realm `bookrush`, Authorization Code + PKCE e tokens somente em memória. Após autenticar, a aplicação consulta `GET /api/v1/books`, oferece busca, detalhes e uma estante local do dispositivo. O backend continua responsável por autorização e pode retornar 401/403 sem que o cliente tente contornar a política.

Validação local:

```bash
cd frontend
npm install
npm run build
npm run lint
```

## Mobile e APK

`mobile/` usa Expo/React Native, o client público `bookrush-mobile` e o mesmo endpoint. O client é provisionado declarativamente em `infrastructure/keycloak/bookrush-realm.json`; a URI `bookrush://callback` deve permanecer registrada no Keycloak. Não há client secret no aplicativo e tokens não são gravados em AsyncStorage ou no bundle. O realm de produto mantém o auto-registro habilitado e o botão `Cadastre-se` abre a tela nativa de registro do Keycloak; depois do cadastro o usuário retorna ao cliente e entra pelo fluxo PKCE normal.

```bash
cd mobile
cp .env.example .env
npm install
npm run typecheck
npx expo run:android
# ou, para um APK de distribuição:
npx eas build --platform android --profile preview
```

`EXPO_PUBLIC_API_URL`, `EXPO_PUBLIC_OIDC_AUTHORITY` e `EXPO_PUBLIC_OIDC_CLIENT_ID` são parâmetros públicos de configuração. Credenciais e tokens continuam externos ao Git.

## Lifecycle

A entidade `bookrush-mobile` está no Location raiz do Backstage e possui TechDocs. Alterações no client Keycloak devem ser reconciliadas pelo procedimento existente antes de distribuir uma nova versão do aplicativo. O cliente web é reconstruído pela imagem `frontend`; o mobile é publicado separadamente como APK/AAB e não é iniciado pelo Compose do servidor.

O protótipo PageLoop foi usado apenas para hierarquia visual e fluxos de descoberta. Feed social, comentários, recomendações e progresso dependem de APIs de produto ainda não existentes e não são simulados como dados reais.


## Fidelidade ao modelo PageLoop

A experiência autenticada usa os mesmos blocos do modelo fornecido: shell, busca, catálogo, biblioteca e leitor. O runtime React consulta o catálogo e o estado do leitor pelas APIs autenticadas; não carrega mais `public/pageloop.js` nem datasets mockados/localStorage como fonte de verdade. O aplicativo mobile mantém o feed vertical escuro e consulta catálogo/biblioteca/eventos por HTTP autenticado.
