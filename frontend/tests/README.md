# Testes do frontend

Os testes usam o test runner nativo do Node e ficam agrupados por comportamento:

- `auth-callback.test.mjs`: callback OIDC e replay do StrictMode.
- `catalog-search.test.mjs` e `search.test.mjs`: contrato de busca server-side,
  paginação, erros e transformação dos resultados.
- `library.test.mjs` e `search-save.test.mjs`: biblioteca, concorrência e
  mutações de salvar/remover.
- `comments.test.mjs`: leitura, publicação e estados de erro dos comentários.

Comandos:

```bash
npm test
npm run test:coverage
```

`test:coverage` usa a cobertura V8 experimental do Node. O relatório atual
valida a execução do harness; os testes que carregam TypeScript compilado por
esbuild em `data:` URL ainda não são mapeados de volta para os arquivos-fonte.
Por isso, o percentual exibido pelo Node não deve ser interpretado como
cobertura real de linhas do código TypeScript. A próxima evolução deve migrar
esses testes para um runner com source maps (por exemplo, Vitest) antes de
adotar um limite de cobertura no CI.
