# Design system do admin

O admin e o leitor usam o pacote local `@bookrush/ui`, em `packages/ui/`.
Componentes, ícones, marca, fontes, tokens e Sidebar têm uma única implementação.
Não existe sincronização de cópias nem script de atualização separado.

O admin fornece rotas, textos, o subtítulo Administração e as ferramentas de
negócio. Os imports antigos de Button e Card são apenas reexports. Para novos
componentes, use imports de `@bookrush/ui`.

Consulte [arquitetura, desenvolvimento e validação](../../docs/development/shared-ui.md)
e a [API do pacote](../../packages/ui/README.md).
