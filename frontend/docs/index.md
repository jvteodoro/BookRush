# Cliente BookRush

O cliente web do BookRush é uma SPA React responsiva. Após autenticar, Para você
usa o novo shell React e o design system BookRush com livros, trechos, curtidas,
biblioteca e impressões reais. Biblioteca também usa o novo shell, com Salvos,
Em leitura e Recentes. Buscar e Recomendações também usam o mesmo shell React.
As outras telas continuam no PageLoop durante
a migração, preservando leitor, perfil,
privacidade, planta, administração e publicador.

## Capacidades atuais

Comentários usam `reader/CommentsDrawer.tsx` e o componente reutilizável
`components/ui/Drawer.tsx`, um diálogo modal com foco contido, fechamento por
Escape, botão ou clique no fundo e restauração de foco pelo navegador. O painel
usa tokens e barra de rolagem do design system nos dois temas. Telas PageLoop
delegam ao React por `bookrush:open-comments`, mantendo um único fluxo visual.

`reader/comments.ts` consulta e publica no social-service. A publicação aceita
até 2000 caracteres, bloqueia envio duplicado e mantém o rascunho quando falha.
Um comentário só entra na lista após confirmação do servidor; a lista e a
contagem do feed são atualizadas em seguida. Nome/avatar abrem o perfil público
existente. `tests/comments.test.mjs` cobre leitura, identidade do autor,
publicação, validação e erros sem publicar comentários reais.

Recomendações usa `reader/RecommendationsPage.tsx`, com os mesmos livros reais
e a ordem do feed carregado no bootstrap autenticado. As fileiras usam `BookRow`,
capas e controles de rolagem compartilhados, com Salvar/Salvo conectado ao estado
de Para você e Biblioteca. A primeira fileira mostra cinco livros; os demais
ficam na segunda. Não são gerados scores ou interesses demonstrativos.

O botão Estou com sorte delega ao fluxo existente via
`window.__BOOKRUSH_LEGACY__.openLucky`, preservando dica, sorteio e abertura no
leitor. A entrada Buscar abre a nova busca. Recomendações também pode ser
acessada a partir das telas PageLoop sem perder o shell React. Para validar,
recarregue o frontend, confira Recomendações, salve/remova um livro, confira
Biblioteca e teste abrir livro e Estou com sorte. As telas não migradas ainda
usam o visual anterior, inclusive o diálogo de sorteio.

Buscar agora usa `reader/SearchPage.tsx` no novo shell React, com resultados,
paginação, capas, salvamento e abertura no leitor. A consulta tem debounce de
300ms e descarta respostas de buscas canceladas. Salvamento compartilha o estado
com Para você e Biblioteca e aguarda confirmação HTTP.

O endpoint publicado com `q` retorna 401 mesmo sem Bearer; nesta etapa,
`reader/search.ts` carrega todas as páginas do catálogo público sem filtro
e pesquisa títulos e descrições em memória, sem acentos ou distinção de caixa.
O catálogo é reutilizado nesta sessão, com até quatro páginas simultâneas;
falhas podem ser tentadas novamente. Não há filtro por autor separado, pois
o contrato público de metadados não retorna nomes de autores. O ajuste da
consulta filtrada no backend fica pendente para uma etapa própria.
`tests/catalog-search.test.mjs` cobre busca, paginação, cache e recuperação;
o teste via proxy local é habilitado por `BOOKRUSH_TEST_SEARCH_LIVE=1`.

O botão Salvar de Buscar chama a mesma API
persistente da biblioteca e só altera o estado visual após confirmação HTTP.
Requisições duplicadas ficam bloqueadas; falhas mantêm o estado anterior e
exibem erro. A regressão está em `tests/search-save.test.mjs`.

A Biblioteca (`reader/LibraryPage.tsx`) consulta a estante e os recentes reais,
resolve os metadados pelo ID de cada livro e busca progresso no reader-state.
Metadados do catálogo usam GET público sem Bearer, pois o catálogo valida o
realm de plataforma; biblioteca, recentes e progresso usam o token do leitor.
Ela não limita a estante aos livros do feed.
Ao retornar de uma tela PageLoop, a Biblioteca recarrega o snapshot completo,
mesmo se o componente continuou montado enquanto estava oculto. Consultas
anteriores são canceladas ao ocultar a página; a atualização do feed não
sobrescreve a consulta da estante enquanto Biblioteca está ativa.
Requisições de metadados têm
concorrência limitada a seis livros. Livros indisponíveis continuam visíveis
para remoção; erros de progresso/recentes são informados sem esconder a estante.
Capas indisponíveis usam o fallback visual do design system.

Salvos permite remover itens após confirmação HTTP; Em leitura mostra progresso
entre 0 e 100%; Recentes respeita a ordem retornada pela API. Abrir um livro
fora do feed registra seus metadados no bridge do leitor antes de delegar ao
Readium. Para testar, salve no feed, visite Biblioteca, abra um livro, volte à
Biblioteca pelo leitor e remova o item. Refaça o login para verificar persistência.
Execute `node --test frontend/tests/*.test.mjs` para os testes do callback e
da carga da biblioteca (sem chamadas reais às APIs).

O componente `src/reader/LiveReader.tsx` adapta o runtime autenticado ao
`ExcerptFeed` compartilhado com a prévia. Não injeta dados demonstrativos no
produto. Curtidas e salvamento aguardam sucesso HTTP antes de atualizar o estado;
falhas preservam o estado anterior e exibem mensagem. Ações concorrentes para
o mesmo livro ficam bloqueadas enquanto a requisição está em andamento.
Uma impressão é registrada quando pelo menos metade do card está visível.

O bridge `window.__BOOKRUSH_LEGACY__` preserva os fluxos atuais de abrir livro,
comentários, compartilhamento e navegação. O evento `bookrush:legacy-route`
devolve o usuário ao novo feed ao clicar em Para você no shell legado. Biblioteca
e curtidas são sincronizadas entre as duas interfaces. A prévia `/home-preview`
continua independente do login e das APIs.

Para validar localmente, entre em `http://localhost:18080/`, confirme o novo
feed com títulos reais e teste curtir/descurtir, salvar/remover, visitar a
biblioteca e voltar a Para você. Refaça o login para verificar persistência;
confira também Ler agora, comentários, compartilhamento e perfil público.
Com o proxy apontando para os serviços publicados, essas ações afetam dados
reais. Build, lint e o teste do callback OIDC são verificações automatizadas;
o fluxo autenticado requer validação no navegador com a conta do usuário.

- login OIDC com Keycloak no realm `bookrush`, usando Authorization Code + PKCE;
- catálogo paginado em `GET /api/v1/books`;
- busca por título;
- detalhes de um livro;
- biblioteca persistida por `reader-state-service`;
- abertura idempotente de livro e eventos de comportamento autenticados;
- layout responsivo para desktop e telas pequenas;
- estados explícitos de carregamento, catálogo vazio, sessão expirada e erro da API.

## Telemetria web e mobile

`src/telemetry.ts` e `mobile/src/telemetry.ts` implementam o mesmo contrato
de eventos em memória. O cliente agrupa no máximo 20 eventos por envio,
mantém no máximo 100 eventos pendentes e repete falhas com backoff exponencial
limitado a 60 segundos. A fila é perdida ao encerrar a sessão ou o processo;
nenhum token, e-mail ou texto do livro entra nela.

O web registra impressões somente quando o elemento atinge o limiar de
visibilidade (`IntersectionObserver`) e mede `DWELL` com duração não negativa.
O mobile expõe os mesmos eventos por `impression` e `dwell`, pois não possui
um DOM; `BOOK_OPEN`, `BOOK_SAVED` e `BOOK_UNSAVED` são enfileirados pelo app.
O envio chama `POST /api/v1/behavior/events` com o bearer apenas no cabeçalho.
Os testes estão em `tests/telemetry.test.mjs` e cobrem sanitização, batching,
retry, viewability e dwell.

A API continua sendo a fronteira de autorização. O navegador não persiste access tokens; a implementação usa memória e estado transitório de protocolo.

## Desenvolvimento

Os ícones de curtida e comentários do feed exibem contagens do social-service.
Após curtir/descurtir, o estado muda somente com sucesso HTTP e a contagem é
reconsultada. `bookrush:social-counts` mantém o feed React sincronizado quando
o diálogo PageLoop publica comentários ou atualiza as contagens.

`br-scroll-area` padroniza a barra de rolagem com tamanho, cores de tema,
trilho e indicador arredondados. Está demonstrada em `/design-system` e aplicada
à citação do feed, cuja barra aparece ao expandir no hover/foco. A citação remove
somente um par completo de aspas externas na apresentação, sem alterar o trecho
original ou suas aspas internas.

`OverflowQuote` limita citações do feed às linhas que cabem no card e mostra
reticências. No hover ou foco, o texto completo fica disponível na mesma área
e pode ser rolado manualmente, sem avançar o feed. Ao sair da área e perder
o foco, retorna ao começo e às reticências. A geometria é recalculada ao
redimensionar ou carregar fontes. O texto completo permanece no nome acessível.

`components/ui/OverflowTitle.tsx` limita títulos longos com reticências e
desliza o texto no hover ou foco do card quando há conteúdo oculto. O nome
completo permanece no tooltip e no nome acessível do livro. O componente mede
o excesso de largura e respeita redução de movimento. `BookCard` reserva altura
fixa para os metadados, mantendo todos os cards e botões da fileira alinhados.

Os componentes reutilizáveis `Switch` e `CarouselControl` fazem parte de
`src/components/ui/` e têm exemplos interativos em `/design-system`.
`Switch` recebe `label` e as propriedades nativas de input, incluindo
`checked`, `onChange` e `disabled`; é anunciado como liga/desliga e pode ser
operado com Tab e Espaço. Sua aplicação inicial é a página Privacidade.
`CarouselControl` recebe `direction` (`previous`/`next`), `label` e propriedades
nativas de botão. Tem fundo discreto, ícone SVG, animações de hover/clique e
estado desabilitado. `BookRow` aplica estes controles circulares sem degradê nas
bordas, visíveis no hover/foco (sempre visíveis em telas touch quando houver
conteúdo a rolar). Os controles ficam indisponíveis nos limites da fileira e
não aparecem quando todos os livros cabem. Ambos usam tokens de tema e
respeitam a preferência de redução de movimento.

Na prévia, os ícones SVG são compartilhados em `components/ui/Icon.tsx`.
A busca do cabeçalho fica centralizada no desktop com um atalho para Para você.
O controle de recolher aparece dentro da sidebar ao passar o mouse ou focar pelo
teclado; minimizado, permanece visível com o ícone de expandir. Separadores
destacam Administração, Publicador e Menu do protótipo.

O feed oculta a barra e mostra um card por vez, com transição vertical de 420ms.
Cada gesto vertical do mouse ou
trackpad avança ou retorna um livro; os eventos de inércia são agrupados até uma
pausa de 180ms. Setas e PageUp/PageDown também navegam quando o feed está focado.
No touch, o scroll usa snap obrigatório. Comentários mantêm rolagem própria.
Os campos de busca usam apenas o cursor de edição ao focar, conforme a
preferência visual definida para esta prévia.

A casinha do cabeçalho fica preenchida e maior em Para você e reage ao hover
e clique. O indicador da sidebar desliza entre os itens selecionados; recolher
e expandir animam a largura do painel e a direção da seta do controle. Todas
essas animações respeitam `prefers-reduced-motion`; com redução de movimento,
o feed passa diretamente ao próximo card.

A base do design system fica em `src/styles/design-system.css`, com tokens
de marca e tokens semânticos para os temas claro/escuro. Os componentes
TypeScript estão em `src/components/ui/`: `Button`, `Card`, `Chip`,
`ProgressBar` e `Logo`. Os estilos usam o prefixo `br-` para coexistir com
o shell PageLoop durante a migração. Não é necessário adicionar Tailwind
ou atualizar React/Vite para consumir esta base.

O `ThemeProvider` disponibiliza `useTheme()` e persiste somente a preferência
visual em `bookrush-theme`. A preferência padrão é escura; os tokens mudam
por `data-bookrush-theme` no elemento raiz, sem recolorir o shell legado.
Use tokens semânticos (`--br-surface`, `--br-content`, `--br-outline`) ao criar
componentes. A fonte de leitura é `--br-font-reading`.

Para conferir a base, rode `npm run dev` e abra `/design-system` no endereço
mostrado pelo Vite. Essa prévia só está disponível em desenvolvimento e não
inicia login ou chamadas de API. Confira os dois temas, larguras desktop/mobile,
foco com Tab, acionamento dos botões e chips pelo teclado, estados desabilitados
e o controle de progresso. Recarregue para confirmar a preferência de tema.
Execute `npm run build` e `npm run lint` antes de integrar componentes às telas.

A prévia da página inicial fica em `/home-preview`, também restrita ao modo
de desenvolvimento e sem autenticação. Ela usa `AppShell`, `BookCard` e
`BookRow` com dados demonstrativos separados das APIs. A navegação permite
conferir Para você, Recomendações, Buscar, Biblioteca, Minha planta, Perfil,
Privacidade, Administração, Publicador e Menu do protótipo, seguindo a sidebar
atual. A entrada é o feed Para você, com cards de texto e ações demonstrativas
de curtida, comentários e salvamento. Compartilhamento exibe uma mensagem
de integração pendente; Administração e Publicador são apresentações visuais,
sem ferramentas reais. No mobile, a barra inferior pode ser rolada para acessar
todos os itens. Clicar em um livro abre detalhes
e permite salvar/remover da biblioteca em memória. Não abre o leitor real.
Filtros por gênero em Recomendações e busca por título/autor funcionam sobre
os exemplos. Os textos do feed são sinopses demonstrativas, sem representar
trechos reais selecionados pelo recommendation-service.

Confira `/home-preview` nos dois temas e em larguras de 375px e 1280px:
sidebar recolhível no desktop, barra inferior no mobile, rolagem das fileiras,
filtros, busca, detalhes e biblioteca. A rota normal `/` abre o novo feed
autenticado com dados reais; feed, Biblioteca, Buscar e Recomendações usam React,
e as demais telas
continuam no shell PageLoop.
A prévia não persiste dados demonstrativos nem envia telemetria.

As logos PNG são as artes do ZIP de referência; as fontes são carregadas do
Google Fonts com fallback local. Otimização das imagens e hospedagem própria
das fontes podem ser tratadas na preparação para produção.

```bash
npm install
npm run dev
npm run build
```

A configuração OIDC pode ser sobrescrita com `VITE_OIDC_AUTHORITY` e `VITE_OIDC_CLIENT_ID`. O cliente padrão é `bookrush-web`, provisionado em `infrastructure/keycloak/bookrush-realm.json`.
Além dos scopes básicos (`openid profile email`), o frontend solicita
`bookrush.catalog.read` para que o bearer emitido pelo Keycloak possa consultar
o catálogo e as thumbnails protegidas. Tokens emitidos antes dessa alteração
precisam ser renovados após novo login; um token já emitido não recebe scopes
retroativamente.

O Vite usa a porta fixa `18080`: abra `http://localhost:18080`, origem permitida
no client Keycloak versionado. Se a porta estiver ocupada, o Vite informa erro
em vez de escolher outra porta que não esteja autorizada. O proxy `/api` aponta
para o gateway Compose `http://localhost:18081`, que encaminha todos os serviços
do produto. Para outro gateway, defina `BOOKRUSH_API_PROXY_TARGET` no
`frontend/.env.local`; essa configuração pertence ao servidor Vite.

Para usar os serviços publicados com o frontend local, os valores públicos são
`VITE_OIDC_AUTHORITY=https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush`,
`VITE_OIDC_CLIENT_ID=bookrush-web` e
`BOOKRUSH_API_PROXY_TARGET=https://bookrush.jteodoro.tec.br`.
Para backend local, use o issuer da instalação local e mantenha o proxy local.
O Keycloak precisa estar disponível, aceitar a origem local e emitir tokens
com o issuer esperado pelas APIs. Alterações no `.env.local` exigem reiniciar
o Vite. Nunca coloque client secrets neste arquivo de configuração do frontend.

O callback OIDC compartilha uma única troca do código de autorização entre
chamadas concorrentes, inclusive quando o React StrictMode repete o efeito em
desenvolvimento. Códigos de autorização não podem ser reutilizados. Após falha,
inicie uma nova tentativa a partir de `/`, sem recarregar a URL antiga de
`/auth/callback`. A regressão é validada sem credenciais ou rede com
`node --test frontend/tests/auth-callback.test.mjs` a partir da raiz.

O shell visual é `frontend/public/pageloop.js` e seu CSS de referência.
`frontend/src/main.tsx` faz a ponte autenticada: injeta os livros retornados
por `GET /api/v1/books` sem Bearer (leitura pública), carrega a biblioteca em
`/api/v1/reader/library` e fornece as
ações de salvar/abrir para o shell. O cliente não usa livros fictícios como
fallback e não usa `localStorage` como fonte de dados; estado transitório da
interface permanece em memória e dados de produto ficam no backend.
O feed usa `GET /api/v1/reader/feed` e registra viewability em
`POST /api/v1/recommendations/impressions/{id}/viewable` antes de abrir um
livro.
Na aba Buscar, `searchCatalog` chama `GET /api/v1/books?q=...&page=...&size=20`
com debounce e paginação; não baixa mais os primeiros 50 livros para filtrar
localmente.
As respostas do catálogo incluem `thumbnailUrl` quando a edição possui uma
thumbnail Gutenberg aprovada. A rota exige o bearer do usuário do realm de
produto; o frontend busca cada imagem com `authClient.fetch`, cria uma URL de
`Blob` apenas em memória; os componentes revogam suas URLs ao desmontar. Isso evita
enviar uma URL protegida diretamente em `<img src>` sem credenciais. O shell
aplica as imagens autenticadas nas capas do feed, recomendações, busca,
biblioteca e sorteio; se a rota retornar `404`, o gradiente gerado permanece
como fallback.
Curtidas, comentários, compartilhamentos, progresso e bookmarks usam os
endpoints dos bounded contexts `social` e `reader-state`; falhas são exibidas
ao usuário e não são mascaradas como sucesso local.
O Nginx do container envia `Cache-Control: no-store` para que uma publicação
não mantenha o bundle anterior no navegador.
Os assets do shell também recebem uma versão explícita na URL para invalidar
caches de borda durante uma publicação.

## Mobile

O aplicativo React Native/Expo está em `mobile/`. Ele compartilha o contrato do
catálogo, usa o client público `bookrush-mobile` e pode gerar APK com `npx expo
run:android` ou EAS. Os tokens também ficam somente na memória.

## Limitações intencionais

Feed social, comentários, progresso de leitura e recomendações dependem de APIs de produto que ainda não existem. A interface mostra o catálogo canônico sem inventar métricas editoriais ou dados sociais.
