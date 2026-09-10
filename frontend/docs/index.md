# Frontend BookRush

React/TypeScript com Vite; Nginx entrega os estáticos e encaminha /api ao catálogo.
A tela consulta /api/status para verificar React → Spring → PostgreSQL.

## Desenvolvimento e testes

```bash
cd frontend
npm install
npm run build
```

Alternativa na raiz: `docker build -t bookrush/frontend:local frontend`.
O componente consome catalog-api e não fornece REST nem eventos próprios.

## Operação

Verifique health do Nginx, console do navegador e resposta /api/status. Em 502,
confira catálogo e gateway. Builds Jenkins selecionam a branch remota.
Não colocar credenciais em variáveis VITE, pois entram no bundle público.
