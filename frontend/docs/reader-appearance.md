# Aparência do leitor

No livro aberto, o botão **Aa** abre o painel de aparência. As alterações são
aplicadas sem remontar a publicação e salvas apenas neste navegador.

- Fontes: Georgia, Arial, Palatino, Verdana e Times New Roman, com alternativas do sistema caso a fonte
  principal não esteja disponível.
- Tamanho: de 85% a 170%, em passos de 5%.
- Espaçamento entre linhas: compacto (1,5), confortável (1,8) e amplo (2,1).
- Largura do texto: estreita (máximo 46rem), confortável (máximo 68rem) e
  ampla (toda a largura disponível). Todas mantêm margens laterais e se
  ajustam à tela do celular. A escolha é salva e reaplicada nos capítulos.
- Temas: branco, claro, rosé, névoa, sépia, grafite, azul noite e noturno,
  sempre com texto e fundo combinados. A paleta usa como referência visual a
  [imagem oficial das configurações do YouVersion](https://help.youversion.com/l/en/article/j2nmx67p54-settings),
  com cores aproximadas próprias do BookRush.
- Títulos do conteúdo acompanham o tamanho em proporções relativas ao texto:
  de 1em (h6) a 1,75em (h1). Isso substitui tamanhos fixos da publicação;
  os controles e o nome do livro na barra de navegação mantêm seu tamanho.
- Restaurar padrão: Georgia, 100%, espaçamento confortável e tema claro.

O painel indica as opções selecionadas e aceita fechamento
com Escape. Em telas de toque, os controles do leitor permanecem visíveis.
Os botões de sumário e aparência alternam a abertura no segundo clique.
Abrir um painel fecha o outro. Painéis têm transições de entrada e saída, e
as opções de aparência usam um contorno deslizante. As animações respeitam
a preferência de movimento reduzido do dispositivo.
Os controles, sumário e título da barra acompanham as cores do tema de leitura.

## Organização

`src/reader/appearance.ts` concentra opções, normalização e persistência.
`appearancePanel.ts` monta os controles usados pelo leitor legado através da
ponte instalada em `main.tsx`. `readiumBridge.ts` aplica as opções à publicação
e reaplica quando um novo frame é carregado. `reader-appearance.css` contém o
estilo do painel.

A chave existente `bookrush-reader-preferences` é preservada. Valores antigos
de espaçamento são aproximados para uma das três opções; cores incompatíveis
são substituídas pelo par do tema. A opção antiga `dyslexic`, que não carregava
uma fonte própria, volta à fonte padrão. Falhas de armazenamento não impedem
usar os controles durante a sessão.

## Validação

Executar `npm run build` e `npm test` em `frontend/`. Os testes cobrem dados
antigos/inválidos, persistência, falhas do armazenamento e os controles.
Conferir também um livro real no navegador: fonte e tamanho, temas, troca de
capítulo, reabertura, Escape e telas pequenas.

Esta mudança é local ao frontend: não altera APIs, autenticação, banco,
containers ou pipelines. Deploy e smoke test no gateway não fazem parte da
validação local. Sumário existente é preservado; destaques e comentários
pessoais continuam fora desta entrega.
