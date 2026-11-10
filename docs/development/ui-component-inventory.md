# Inventário da interface compartilhada

## Fonte e consumidores

O levantamento abrange os elementos visuais comuns; modelos e operações de
catálogo, moderação, leitura e treinamento continuam nas respectivas features.
Não há motivo para duplicar a aparência desses elementos em cada aplicação.

| Elemento | Componente em `packages/ui/src/components/` | Uso no app | Uso no admin |
| --- | --- | --- | --- |
| Estrutura, conteúdo e espaçamento da página | AppFrame | components/layout/AppShell | components/Layout |
| Cabeçalho, busca global e ações | AppHeader | AppShell | Layout |
| Campo de busca | SearchField | AppHeader e reader/SearchPage | Cabeçalho, catálogo, analytics, review e moderação |
| Marca e avatar | Logo, Avatar | AppShell/AppHeader | Sidebar e Layout |
| Navegação | Sidebar | AppShell e app/LegacySidebar | components/Sidebar |
| Título e descrição de página | PageHeader | SearchPage | Dashboard e páginas das features |
| Barra de ações e resumo | Toolbar | SearchPage | Features administrativas |
| Superfícies e cards | Card, Surface | Cards do leitor | Painéis e estados das features |
| Abas/filtros de seção | Tabs, Button | SearchPage | Review, treinamento e operações |
| Campos textuais e numéricos | Input | Avaliações e formulários React | Formulários das features |
| Seleção e texto longo | Select, Textarea | Comentários e controles imperativos | Filtros, formulários e moderação |
| Mensagens de erro e aviso | Notice | Páginas do leitor e comentários | Features administrativas |
| Primitivas existentes | Button, Chip, Switch, ProgressBar, Drawer, Icon, CarouselControl | Leitor | Consumo pelo mesmo pacote |

Os componentes extraídos preservam callbacks, permissões, formulários e dados
dos consumidores. O cabeçalho do admin usa a variante compacta do AppHeader:
título e ThemeToggle, sem busca global, atalho de início ou avatar. O campo
local usa SearchField e mantém a filtragem existente. Componentes de
domínio, como o conteúdo de uma tabela de jobs ou de um livro, não são cópias de
elementos do leitor e permanecem nas features.

## Sidebar: igualdade por import e propriedade do código

`frontend/src/components/layout/AppShell.tsx`,
`frontend/src/app/LegacySidebar.tsx` e
`admin-web/src/components/Sidebar.tsx` importam **`@bookrush/ui/Sidebar`**.
Esse export aponta para **`packages/ui/src/components/Sidebar.tsx`**.

Somente **`packages/ui/src/styles/sidebar.css`** define a largura, o padding,
a fonte, o recolhimento e os breakpoints da Sidebar. Os dois entrypoints
importam `@bookrush/ui/sidebar.css`. Não há prop de largura no componente nem
uma largura específica do admin. App e admin também consomem o mesmo AppFrame
e seu CSS de layout. O subtítulo Administração é um slot de conteúdo.

As regras originais de estrutura e cabeçalho foram movidas de reader-shell e
reader-refinements para **`packages/ui/src/styles/layout.css`**, importado por
ambos como `@bookrush/ui/layout.css`. As antigas implementações do admin para
busca, cabeçalho e inputs foram substituídas pelos imports acima.

## Proteção contra regressão

`packages/ui/check-consumers.mjs`, executado no lint das duas aplicações,
rejeita definições locais de Sidebar, indicador, cabeçalho, busca e campos
compartilhados. Isso também roda nos builds Docker, que já executam o lint.
Novas variantes devem ser adicionadas ao pacote, não por overrides locais.

As telas imperativas em `pageloop.js` mantêm os nós e eventos dos formulários.
O adaptador `styleNativeControls`, do mesmo pacote, aplica as receitas visuais
das primitivas nesses nós sem remover listeners ou mudar suas operações.

`npm run test:controls` no admin verifica o cabeçalho sem controles extras,
a troca e persistência do tema, a busca local e a ausência de contorno adicional
de foco nas buscas e seletores. Não mede dimensões. Os builds
e o lint validam os consumidores. A inspeção da Sidebar nesta alteração é feita
por suas referências de código, conforme solicitado, sem teste de tamanho.

Validação de APIs reais, autenticação e produção não faz parte dessa prévia.
As alterações permanecem locais até publicação e deploy autorizados.

## Evidências desta extração

Builds, suites unitárias, cobertura, lint e formatação passaram nos estágios
Docker dos dois consumidores. O teste funcional `test:controls` passou no Chrome
com dados simulados. O verificador de propriedade de estilos passou. Nenhum
teste de dimensão foi executado nesta extração. Beads permanece indisponível
no ambiente; não houve registro de tarefa nem publicação/deploy.
