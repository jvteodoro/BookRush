# BookRush Mobile

Aplicativo React Native/Expo para descoberta de livros em Android e iOS. O app usa o realm de produto do Keycloak com Authorization Code + PKCE e consulta o mesmo endpoint canônico de catálogo usado pelo cliente web.

Consulte o [guia de execução e APK](../README.md) para configurar o client `bookrush-mobile`, executar localmente e gerar um APK. A aplicação mantém tokens em memória e não contém segredos.

The mobile client uses the reader-state and behavior APIs for saved books and
batched book-open, save, impression and dwell events; tokens remain in memory.
The telemetry queue is bounded, retries with exponential backoff and never
persists credentials or book text. The CI export requires `expo-asset`, pinned
to the Expo SDK 52-compatible `~11.0.5` version in `package-lock.json`.

## Sessão de produto

O aplicativo mantém Authorization Code + PKCE e tokens em memória. Depois da
troca do código, consulta `/api/v1/session` e só monta catálogo, biblioteca e
telemetria com PRODUCT_USE. Ao retomar o aplicativo, a sessão é reavaliada.
A experiência restrita usa os mesmos endpoints de convites do web e informa
quando a regularização depende de operador. Falhas de autorização removem
a experiência anterior. Um salvamento otimista é revertido quando a API
rejeita a operação, e a telemetria de salvamento só é emitida após sucesso.
