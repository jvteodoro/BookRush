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

O SDK `frontend/src/telemetry.ts` mantém uma fila em memória, envia lotes ao
behavior-service com retry limitado e suporta impressão por
`IntersectionObserver` e dwell. Payloads são reduzidos por allowlist
estrutural, sem texto livre, tokens ou identificadores pessoais.

O portal PageLoop também consulta o `publisher-service` quando o usuário
autenticado abre a área de publicador. Rascunhos e submissões são criados e
enviados por `POST /api/v1/publisher/submissions` e
`POST /api/v1/publisher/submissions/{id}/submit`; a lista persistida vem de
`GET /api/v1/publisher/submissions`. Essas rotas são encaminhadas pelo
Traefik diretamente ao serviço de publicações, sem passar pelo catálogo.
Métricas editoriais ainda dependem do contrato de analytics do publicador e
não são inventadas pelo frontend.

O upload do publicador segue duas fases: solicitar a URL PUT temporária e
finalizar informando o SHA-256. O binário não passa pelo frontend BookRush nem
é salvo no PostgreSQL; somente a intenção, objeto, tamanho e hash são
persistidos pelo publisher-service.
No Compose, o recurso é opt-in (`PUBLISHER_STORAGE_ENABLED=false` por padrão)
para que um clone sem credenciais S3 não derrube o serviço de metadata. Para
habilitá-lo, forneça as credenciais do SeaweedFS no `.env`, crie/valide o bucket
de staging e então reconcilie o container publisher.
O formulário web executa esse fluxo completo para EPUB/PDF: cria o rascunho,
envia o arquivo diretamente pela URL assinada, calcula SHA-256 no navegador,
finaliza o upload e só então solicita a transição para revisão.
Quando a submissão possui `catalogBookId`, a aba de estatísticas consulta os
agregados reais do behavior-service por publicação. Uma submissão sem vínculo
canônico aparece como `NOT_LINKED`, sem números inventados.

Para associar uma submissão a uma execução, inclua `publisherSubmissionId` nos
parâmetros do comando de ingestão. Após a canonização, o ingestion-service chama
o callback interno do publisher e preenche automaticamente `catalogBookId`; o
vínculo é idempotente e não permite substituir uma referência já confirmada.

As ações sociais usam os payloads do contrato vigente: comentários enviam
`{body}`, compartilhamentos enviam `{channel: "copy-link"}` e seguir/deixar de
seguir usa `PUT`/`DELETE /api/v1/social/users/{subject}/follow`. O frontend não
faz fallback local quando uma dessas operações falha.

Em Compose, `reader-profile-service` e `social-service` validam tokens do
realm de produto (`bookrush`) e `publisher-service` valida tokens do realm
administrativo (`bookrush-platform`) com a audiência de ingestão. Os valores
podem ser sobrescritos por variáveis `BOOKRUSH_*_SECURITY_*` no ambiente; os
defaults mantêm a separação de confiança e não deixam esses endpoints
anônimos.

O portal administrativo consulta `/api/v1/admin/users` e
`/api/v1/admin/reports` pela facade `admin-service`. Sem token administrativo a
resposta é `401/403`; o frontend mantém a fila vazia quando a conta não tem
essa permissão, em vez de preencher dados fictícios.

Ao abrir um livro, o cliente consulta `/api/v1/content/books/{id}/chapters`.
O índice lateral usa a projeção de capítulos persistida e sua versão textual;
quando a obra ainda não tem projeção, a interface informa essa condição em
vez de fabricar capítulos. O `book-content-service` agora expõe um Web
Publication Manifest por capítulos e recursos HTML derivados da versão textual
normalizada; o frontend monta o `WebPubNavigator` do Readium com um
`HttpFetcher` próprio que injeta o bearer em cada recurso da publicação. Sem
esse fetcher o Readium usa `EmptyFetcher` e falha com `This is an empty fetcher`.
Obras sem
projeção textual disponível permanecem indisponíveis até a ingestão concluir o
artifact autorizado.

O perfil autenticado é carregado de `GET /api/v1/profile`; nome, bio e estado
público deixam de depender da lista de usuários do protótipo. A edição e a
privacidade persistem por `PUT /api/v1/profile`. A administração exibe somente
usuários e denúncias retornados pela facade, e decisões são registradas por
`POST /api/v1/admin/moderation`; não há bloqueios ou métricas simuladas no
cliente.

A tela do leitor mantém somente o `WebPubNavigator` do Readium: não há texto de exemplo, slider de paginação, controle manual de fonte/tema ou barra móvel paralela. O shell fornece apenas metadados do livro, progresso persistido e marcador; navegação, paginação e renderização pertencem à publicação Readium.

O layout atual do leitor usa uma única coluna responsiva: um cabeçalho compacto para retorno e marcador, o `readium-host` centralizado para o WebPubNavigator e o progresso fixado na base. Os painéis laterais do protótipo antigo não participam da rota de leitura.


O leitor ocupa a viewport inteira para preservar a área útil do Readium. Os comandos externos ficam ocultos até foco/hover e funcionam como uma camada mínima de retorno e marcador, deixando o conteúdo e a paginação sob responsabilidade do navegador Readium.


A tela de leitura também suprime o seletor global do protótipo enquanto o Readium está aberto, evitando que o modo mobile/web apareça sobre a publicação.


Ao sair da rota de leitura, o frontend destrói explicitamente o `WebPubNavigator` antes de uma nova abertura. Isso evita manter um iframe/blob antigo e garante que uma reabertura recrie o fetcher autenticado e a publicação atual.


O fetcher autenticado usa `cache: no-store` para manifesto e recursos da publicação, evitando que uma segunda montagem reutilize um blob/iframe inválido. A saída aguarda `unmount()` terminar antes de renderizar a rota anterior.


O host do Readium começa vazio; o frontend não deixa um texto de carregamento sobreposto ao iframe, e só renderiza uma mensagem quando a montagem falha.


A sessão OIDC mantém o `User` retornado pelo callback PKCE em memória durante a execução do app. Isso garante que bridges PageLoop também recebam o bearer; tokens continuam fora de localStorage/sessionStorage e são removidos em logout/401.


Respostas `401` de endpoints opcionais (administração/publicador) não removem mais a sessão OIDC global. Isso evita que uma API sem permissão faça as chamadas de abertura/eventos perderem o bearer; a sessão só é limpa quando o refresh PKCE falha ou no logout.


Comentários preservam `subject_key` como identidade técnica, mas resolvem o nome de exibição público no `reader-profile-service`. O valor é salvo como snapshot no comentário e também atualizado na leitura quando o perfil público estiver disponível; email nunca é usado como chave.

O contrato interno `GET /api/v1/profile/public?subject=...` retorna `displayName` somente quando `is_public=true`; o `social-service` encaminha o bearer do usuário ao consultar esse contrato e expõe o resultado como `authorName` nos comentários.
