# @bookrush/ui

Fonte única dos componentes e estilos usados pelo leitor e pelo admin.
Os dois projetos dependem deste pacote com `file:../packages/ui`; não há etapa
de cópia, publicação em registry ou sincronização manual.

Inclui Button, Card, Chip, Switch, ProgressBar, Logo, Icon, Drawer, controles de
carrossel, textos com overflow, ThemeProvider e Sidebar. Tokens, tipografia,
estados, foco e movimento reduzido ficam em `src/styles/`.

AppFrame, AppHeader, SearchField, Avatar, PageHeader, Toolbar, Surface, Input,
Select, Textarea, Tabs e Notice compõem a base de layout e controles extraída
do app. Importe também `@bookrush/ui/layout.css`. O lint das duas aplicações
verifica a ausência de overrides locais dos componentes protegidos. Consulte o
[inventário](../../docs/development/ui-component-inventory.md) para os consumidores.

```tsx
import { Button } from '@bookrush/ui/Button';
import { Sidebar } from '@bookrush/ui/Sidebar';
import '@bookrush/ui/styles.css';
import '@bookrush/ui/sidebar.css';
```

As entradas antigas `src/components/ui/` são reexports de compatibilidade.
Novos consumidores devem importar o pacote. A aplicação fornece à Sidebar os
itens, rota ativa, callbacks e conteúdo do rodapé; o admin fornece também
`subtitle="Administração"`. Medidas, animações e marca pertencem ao pacote.
Não acrescente overrides locais às classes `br-sidebar*` ou `br-button*` para
reproduzir variantes. Implemente a variante no componente compartilhado.

O plugin `bookrushUi()` de `@bookrush/ui/vite` resolve os imports para os
fontes originais, permite seu carregamento pelo Vite e mantém uma instância
React por aplicação. Edições no pacote atualizam as duas prévias. TypeScript
e os testes esbuild preservam os symlinks para resolver os peers pelo consumidor.

Depois de clonar, execute `npm ci` em `frontend/` e `admin-web/`. Os comandos
`npm run lint` e `npm run format:ui` dos consumidores também verificam este
pacote. Para alterações visuais, rode o teste comparativo documentado em
[docs/development/shared-ui.md](../../docs/development/shared-ui.md).
