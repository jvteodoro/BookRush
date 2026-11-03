# Frontend API routing

As URLs da API usadas pelo frontend ficam centralizadas em
`frontend/src/app/apiRoutes.ts`. Componentes e adaptadores devem usar esse
mapa, em vez de montar strings `/api/...` localmente.

O `reader-bff-service` é o gateway das operações autenticadas do leitor:
feed, recomendações, biblioteca, progresso, bookmarks, eventos de abertura,
impressões e a publicação EPUB (manifesto e recursos). O `readiumBridge`
remapeia os `href` de `/api/v1/content/books/...` para o prefixo equivalente
do BFF e preserva o bearer token através do `authClient`.

As rotas de catálogo, social, perfil, comportamento, publisher e administração
também são declaradas no mapa, mas continuam apontando para seus serviços
donos. Isso deixa explícita a ownership sem transformar o BFF em um proxy
genérico e permite mudar prefixos em um único ponto.

Ao adicionar uma rota autenticada do leitor, atualize conjuntamente:

1. `frontend/src/app/apiRoutes.ts`;
2. o controller e o OpenAPI do `reader-bff-service`;
3. a documentação e os testes do serviço;
4. os testes/build do frontend e o smoke test pelo gateway.
