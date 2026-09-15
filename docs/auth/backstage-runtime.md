# Backstage em runtime público

O bundle estático do Backstage é compilado a partir da configuração base. Em
produção, a configuração OIDC é aplicada pelo overlay de runtime, portanto o
container deve usar `NODE_ENV=production` (o Compose já fornece esse default).

Em hosts públicos, o frontend seleciona o provedor OIDC pelo ambiente de
produção ou pelo hostname não local. Em desenvolvimento local (`localhost` ou
`127.0.0.1`), o provedor guest continua disponível quando o OIDC não está
habilitado.

Após publicar uma nova imagem, faça um hard reload no navegador caso ele ainda
esteja usando um bundle antigo. A validação operacional mínima é:

```bash
docker inspect bookrush-portal-backstage-1 \\
  --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^NODE_ENV='
curl -fsS http://127.0.0.1:17007/ >/dev/null
docker logs --tail 100 bookrush-portal-backstage-1
```

O log não deve mostrar um loop de `GET /api/auth/guest/refresh` em um host
público. Se o DNS público não resolver durante um teste local, valide pela
porta loopback ou pelo hostname configurado no arquivo hosts.

O plugin de documentação de APIs é registrado explicitamente no app e usa sua
fábrica oficial de widgets. Para o botão **Try it out**, o widget OpenAPI usa um
interceptor que obtém o access token OIDC em memória e o envia apenas para
hosts BookRush permitidos.

O interceptor é instalado por um *override* da extensão existente
`api:api-docs/config`, aplicado com `apiDocsPlugin.withOverrides(...)`. O
`App` carrega essa variante do plugin. O override deve produzir a fábrica
original com as dependências adicionais, nunca entrar na lista de extensões do
módulo `app`, registrar outro `ApiBlueprint` ou criar outra fábrica para
`apiDocsConfigRef`. Dois fornecedores da API `plugin.api-docs.config` impedem o
frontend de iniciar com `API_FACTORY_CONFLICT`; uma fábrica manual também pode
deixar `api` indefinida durante a inicialização.

As chamadas **Try it out** que usam `Authorization` fazem preflight para a API.
O catálogo permite somente a origem configurada em
`CATALOG_CORS_ALLOWED_ORIGINS` (por padrão,
`https://docs-bookrush.jteodoro.tec.br`) e apenas `GET`/`OPTIONS` na rota
interna de consulta. O cliente Keycloak `bookrush-backstage` recebe somente o
escopo de leitura `bookrush.catalog.read` e a audiência
`bookrush-catalog-admin`; ele não recebe escopo de escrita.

O portal também define `backend.csp.connect-src` no overlay Docker. A diretiva
mantém a política CSP padrão e permite conexões somente com a própria origem e
com `https://bookrush.jteodoro.tec.br`, que é a origem pública das APIs usadas
pela documentação OpenAPI. Sem essa exceção, o navegador bloqueia a requisição
antes mesmo de CORS ou autenticação serem avaliados e apresenta `NetworkError`.

O Backstage exige a API de Signals para o armazenamento do frontend, embora
esta instalação não tenha um transporte de eventos publicado. O módulo local
fornece uma implementação explícita sem eventos: ela preserva o contrato de
armazenamento, sem iniciar conexões WebSocket de reconexão para `/api/signals`.
Quando a plataforma adotar um barramento de sinais real, esse módulo deve ser
substituído pelo plugin oficial de Signals e pelo backend correspondente.

O bloqueio do script de métricas injetado pela Cloudflare continua intencional:
ele não é necessário ao Backstage nem às chamadas Swagger.
