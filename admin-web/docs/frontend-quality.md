# Qualidade do admin-web

## Organização

O admin-web é dividido por responsabilidade:

- `src/app/AuthGate.tsx`: estado de autenticação e entrada no router;
- `src/app/adminRouter.tsx`: rotas da console;
- `src/components/Sidebar.tsx` e `Layout.tsx`: shell e navegação;
- `src/features/`: telas por domínio, como catálogo, review e analytics;
- `src/lib/`: configuração, HTTP autenticado e serviços.

Uma tela nova deve ficar em sua feature e consumir `services`, sem criar
chamadas HTTP diretamente no componente. O `AuthProvider` continua sendo o
único responsável por inicializar o Keycloak e atualizar o token.

## Validação local

Na pasta `admin-web`, execute:

```bash
npm test
npm run test:coverage
npm run format:check
npm run build
npm run lint
```

O teste atual cobre a configuração da navegação. A cobertura V8 do Node é uma
verificação do harness; o teste compila TypeScript via esbuild em `data:` URL e
o relatório ainda não mapeia essas linhas para os arquivos-fonte. Não adote um
limite de cobertura no CI até migrar os testes para um runner com source maps.
