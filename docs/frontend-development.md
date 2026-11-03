# Desenvolvimento do frontend

## Organização

O entrypoint `frontend/src/main.tsx` deve permanecer como composição da
aplicação. Integrações e responsabilidades reutilizáveis ficam separadas:

- `src/app/`: tipos compartilhados, loaders de API, navegação e shells de tela;
- `src/reader/`: componentes e integrações específicas do leitor;
- `src/auth.ts`: cliente OIDC e chamadas autenticadas;
- `tests/`: testes por comportamento, sem depender de uma ordem global.

Ao criar uma nova tela ou integração, extraia primeiro o estado e os efeitos
para um hook/serviço quando eles não forem específicos da apresentação. Use
`authClient.fetch` para APIs protegidas e mantenha o tratamento de loading,
`401`, `403`, `404` e erro de rede explícito na interface.

## Testes e qualidade

Na pasta `frontend`, execute:

```bash
npm test
npm run test:coverage
npm run format:check
npm run build
npm run lint
```

Os testes atuais usam o runner nativo do Node. A cobertura V8 é executada como
verificação do harness; módulos TypeScript compilados por esbuild em `data:` URL
ainda não são mapeados para as linhas dos arquivos-fonte. Não use o percentual
retornado como um limite de cobertura de produção até migrar esses testes para
um runner com source maps, como Vitest.

## Shell autenticado e migração do legado

As abas autenticadas devem ser renderizadas pelo shell React em
`src/components/layout/AppShell.tsx`. `src/reader/LiveReader.tsx` compõe as
abas de feed, recomendações, busca, biblioteca e as áreas de conta. As telas
de planta, perfil, privacidade, administração e publicador ficam em
`src/reader/AccountPages.tsx` e usam `Card`, `Button`, `Switch` e os tokens
`--br-*` do design system.

`public/pageloop.js` permanece somente como ponte temporária para o leitor de
livro legado e integrações que ainda dependem dele. Não adicione novas telas
ou navegação ao pageloop: uma rota nova deve ser um componente React dentro do
`AppShell`, com loading, erro e ações autenticadas explícitos.

O `admin-web` é uma aplicação React separada, implantada pelo job
`bookrush-admin-web`. Ela deve manter a inicialização do Keycloak protegida por
tratamento de rejeição, renderizando uma mensagem acionável em vez de deixar o
root vazio quando a sessão não puder ser restaurada. Alterações visuais devem
reutilizar os tokens BookRush definidos em `admin-web/src/styles/app.css` e
validar o build do admin separadamente do frontend principal.

## Feed e resumo de leitura

O card de trecho ocupa toda a largura disponível do conteúdo do feed, sem
coluna lateral. Os controles de curtir, comentar, salvar e compartilhar
permanecem no card, assim como a navegação entre trechos.
Na aba de feed, o shell ocupa a altura da janela e o card usa o espaço entre
o cabeçalho e a navegação inferior, sem rolagem da página inteira. Apenas o
feed e os trechos longos têm rolagem interna. O topo do card usa espaçamento
compacto; telas baixas também compactam as aspas e os controles. Ao sair do
feed, as demais abas continuam com a rolagem normal da página.
A linha de idioma/gênero e posição no feed foi removida do card. No feed real,
o trecho usa o espaço vertical restante entre as aspas e os dados do livro,
em vez de ficar limitado a uma caixa de 15 rem.

As informações de sequência, descoberta e livros salvos ficam na aba Perfil,
no componente `src/reader/ReadingSummary.tsx`. Os números usam o mesmo estado
real de sequência e biblioteca do leitor. Os atalhos abrem Minha planta,
Recomendações e Biblioteca. Em telas menores, os blocos se reorganizam em uma
coluna. A prévia local `/home-preview` reproduz a mesma organização com dados
demonstrativos.

## Privacidade

A página de privacidade usa os tokens de superfície, cores e sombras do design
system, com ícones compartilhados e opções arredondadas de audiência. Cada
preferência é um grupo de radios nativos com legenda e descrição acessíveis;
a seleção é destacada, aceita navegação por teclado e fica desabilitada durante
o salvamento. As opções Todos, Seguidores e Somente eu mantêm o contrato e a
persistência existentes. Os grupos se reorganizam em telas menores e seguem
o tema claro ou escuro.

## Leitor EPUB e Readium

O leitor usa `src/reader/readiumBridge.ts` para montar a publicação protegida
em `publication.json`. As preferências de fonte, tamanho, espaçamento, cor do
texto e cor do fundo devem ser aplicadas ao documento do iframe e submetidas ao
`WebPubNavigator`; alterar somente o estado do menu não é suficiente.

A navegação estrutural vem do `toc` do manifesto Readium, com fallback para a
`readingOrder`. O bridge expõe essa estrutura ao PageLoop e navega por `goLink`,
`goForward` e `goBackward` usando os callbacks da API do Readium. A API de
capítulos do backend permanece apenas como compatibilidade para publicações
antigas que ainda não possuem TOC no manifesto; não se deve inventar uma lista
paralela de capítulos no frontend.

Para EPUBs, o manifesto é construído pelo `book-content-service` a partir do
arquivo original: o `spine` define a ordem de leitura e o NCX ou `nav.xhtml`
define a árvore de capítulos. O Readium recebe XHTML, CSS, imagens e fontes
pelas rotas autenticadas de recursos do EPUB. TXT e `chapters.json` são usados
para busca, analytics e fallback, nunca como substitutos da estrutura editorial
quando o EPUB está disponível.

### Attribution da sessão de leitura

Quando um livro é aberto a partir do feed, `main.tsx` registra temporariamente
`recommendationItemId` e `impressionId` como origem solicitada. O
`readerSessionOrigin` compartilhado captura essa origem antes de desmontar o
reader anterior e a ativa somente depois que a nova sessão for iniciada pelo
`readiumBridge`. O endpoint de progresso consulta apenas a origem da sessão
ativa, de modo que `READ_PROGRESS` e `BOOK_COMPLETE` preservem a mesma
correlation. A abertura por busca, biblioteca ou rota direta não cria
attribution, e a desmontagem limpa a sessão anterior antes de instalar a
seguinte.
