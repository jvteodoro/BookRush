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
