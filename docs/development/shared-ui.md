# Componentes compartilhados do BookRush

Veja o [inventário de componentes, consumidores e propriedade dos estilos](ui-component-inventory.md).

`packages/ui` é a fonte única da interface comum ao leitor e ao admin.
Ambos continuam com builds, autenticação e rotas independentes. As diferenças
de negócio ficam nas aplicações; aparência e comportamento dos componentes
comuns ficam em `@bookrush/ui`.

## Desenvolvimento

Execute `npm ci` em `frontend/` e `admin-web/`. Os lockfiles registram o mesmo
pacote local. Não copie componentes ou assets entre as aplicações. Os antigos
caminhos de Button, Card e demais primitivas no leitor apenas reexportam o
pacote para preservar imports existentes.

O plugin Vite do pacote resolve os fontes diretamente, com React deduplicado.
O modo dev seleciona explicitamente `vite.config.ts`. Mudanças nos exports do
pacote reiniciam a configuração do Vite automaticamente. Servidores iniciados
antes desse mecanismo precisam de um reinício inicial para carregá-lo.
Isso permite atualizar as duas prévias ao salvar um componente compartilhado.
A Sidebar recebe itens, ícones, navegação, estado de recolhimento e rodapé como
props; o subtítulo Administração é preservado no admin. Os dois usam os mesmos
estilos, medidas, fontes e indicador animado, inclusive na navegação mobile.
O admin usa Logo com tema claro; no leitor a marca acompanha o ThemeProvider.

As telas de produção montadas por `pageloop.js` também usam a mesma Sidebar,
através do portal `LegacySidebar` no `AuthenticatedShell`. O script legado
fornece apenas o slot e eventos de navegação; sua antiga sidebar e seus estilos
foram removidos. Assim, perfil, privacidade, publicador, leitor React e admin
não mantêm implementações paralelas da navegação.

Os estilos específicos das ferramentas administrativas e do catálogo ficam em
`admin-web/src/styles/admin-layout.css`. Não sobrescreva as medidas e a
tipografia dos componentes compartilhados nesse arquivo.

## Validação

Nos dois consumidores execute `npm run build`, `npm run lint`,
`npm run format:check`, `npm run format:ui` e `npm test`. O lint inclui os fontes
do pacote. A resolução de symlinks também está configurada nos testes que
transpilam componentes React com esbuild.

Em `admin-web/`, o teste `npm run test:ui` inicia duas prévias temporárias com
dados simulados, monta o `AuthenticatedShell` de produção com o `pageloop.js`
e CSS reais, compara estilos computados das barras e testa navegação,
indicador, recolhimento, movimento reduzido e ausência de overflow no catálogo
em 1440, 1024, 800 e 390 px. Ele fecha as prévias ao terminar. Instale o browser
com `npx playwright install chromium`, ou use o Chrome instalado:

```powershell
$env:PLAYWRIGHT_CHANNEL = 'chrome'
npm.cmd run test:ui
```

As capturas ficam em `admin-web/artifacts/shared-ui/`. Esse teste não autentica
em APIs reais e não substitui smoke de produção.

## Docker e Jenkins

Os dois Dockerfiles usam o repositório como contexto e copiam explicitamente
seu consumidor, `packages/ui` e o lint compartilhado. Execute na raiz:

```sh
docker build --target build -t bookrush/frontend-ui-check -f frontend/Dockerfile .
docker build --target build -t bookrush/admin-ui-check -f admin-web/Dockerfile .
```

Compose, Jenkins e os scripts que constroem o admin usam esse mesmo contexto.
O `.dockerignore` da raiz exclui ambientes, caches, dependências locais e
artefatos. Mudanças em `packages/ui/` exigem documentação no change gate.
Não há mudança de contrato HTTP, migration, permissões ou CORS nesta extração.
Nenhum deploy ou reinício de serviços é necessário para revisão visual.

## Evidências da extração

- Builds locais de frontend e admin passaram.
- Teste de igualdade de medidas e comportamento passou no Chrome.
- Suites completas, cobertura, lint e formatação passaram nos estágios Docker
  de ambas as aplicações, inclusive os testes de moderação em Linux.
- Change gate passou e os Jenkinsfiles principais têm conteúdo idêntico.
- Os contratos das pipelines e seus 12 testes passaram em container Linux;
  a cópia temporária normalizou CRLF sem reescrever o checkout Windows.
- Beads não está instalado no host nem no ambiente WSL; o registro persistente
  não pôde ser atualizado nesta sessão.
- Jenkins, deploy e smoke por gateway público não foram executados. A validação
  descrita é local e em containers de build.
