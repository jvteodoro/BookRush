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
