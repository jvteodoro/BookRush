# BookRush Mobile

Aplicativo React Native/Expo para leitores. A experiência mobile segue a linguagem visual do protótipo PageLoop, mas usa o nome, autenticação e catálogo reais do BookRush.

## Desenvolvimento

```bash
cd mobile
cp .env.example .env
npm install
npm start
```

O cliente usa Authorization Code + PKCE com o realm de produto `bookrush`. Crie no Keycloak um client público `bookrush-mobile`, com redirect URI `bookrush://callback` e web origins compatíveis. O aplicativo não contém client secret e mantém o access token somente em memória.

A API é configurada por `EXPO_PUBLIC_API_URL` e consulta `GET /api/internal/v1/catalog/books`. Esse endpoint permanece protegido pelo Resource Server; a autenticação do aplicativo não substitui autorização no backend.

## APK

Para um APK local, com Android SDK configurado:

```bash
npx expo run:android
```

Para gerar um APK reproduzível usando EAS Build:

```bash
npx eas build --platform android --profile preview
```

A configuração `eas.json` usa `buildType: apk` no perfil `preview`. Nenhum token, senha ou segredo deve ser colocado em `.env`, `app.json` ou no bundle; os valores `EXPO_PUBLIC_*` são apenas configuração pública.
