# Autorização de produto v1 no Spring MVC

O módulo `bookrush-security-spring-boot-starter` oferece a integração opt-in
com o contrato versionado em `docs/productization/product-access-v1`.
O avaliador recebe as ações do contrato e consulta os fatos nos serviços donos.
O starter aplica a resposta antes de executar o handler MVC.

## Configuração

Configure o JWT humano na chain padrão do starter ou no resource server da
chain customizada. O JWT precisa ter assinatura e tempo validados pela chain.
A política de cada handler confere issuer, audience, cliente humano `azp` e
escopos novamente, antes da consulta. Para a chain padrão, use
`bookrush.security.enabled=true` e configure `issuer`, `jwk-set-uri` e
`audience` em `bookrush.security`.

```yaml
bookrush:
  security:
    product-access:
      enabled: true
      decision-base-uri: ${PRODUCT_ACCESS_ORIGIN}
      token-uri: ${PRODUCT_ACCESS_TOKEN_URI}
      jwk-set-uri: ${PRODUCT_ACCESS_JWKS_URI}
      workload-issuer: ${PRODUCT_ACCESS_WORKLOAD_ISSUER}
      client-id: ${PRODUCT_ACCESS_CLIENT_ID}
      client-secret: ${PRODUCT_ACCESS_CLIENT_SECRET}
      audience: bookrush-family
      scope: product-access.evaluate
      connect-timeout: 500ms
      request-timeout: 2s
```

A origem é interna e a rota de decisão é fixa. Provisione no realm técnico um
cliente confidencial exclusivo do consumidor e registre sua allowlist de
ações e recursos no avaliador. O starter autentica por `client_credentials`,
valida assinatura RS256 via JWKS e exige issuer, audience, scope, expiração
e `azp` esperados. O JWT humano fica no consumidor. O segredo vem da
configuração protegida do runtime. HTTPS é o padrão. Use
`allow-insecure-http: true` somente no ambiente local descartável.
Configuração essencial ausente ou inválida impede a inicialização.

## Declare as operações do serviço

Registre um bean `ProductAccessRoutes`. Use método e padrão MVC exato, como em
`routes.tsv` do contrato. O starter registra um interceptor que usa o
`AuthorizationManager` após a chain existente, perto da entrada do controller.
A mesma integração funciona com a chain padrão e com uma chain customizada,
inclusive quando a customizada tem uma regra mais ampla que a regra de produto.

```java
@Bean
ProductAccessRoutes productRoutes(Books books, ProductIssuer issuer) {
  return ProductAccessRoutes.builder()
      .protect("GET", "/books/{id}", new ProductAccessRoutes.Policy(
          issuer.value(), "bookrush-catalog", Set.of("bookrush-web"), Set.of("catalog.read"),
          ProductAccessRoutes.Action.PRODUCT_USE, ProductAccessRoutes.Type.BOOK, true,
          (request, actor) -> {
            var path = (Map<?, ?>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            var book = books.loadOrHide((String) path.get("id"), actor);
            return new ProductAccessRoutes.Resource(ProductAccessRoutes.Type.BOOK, book.id(), book.owner());
          }))
      .existingSecurity("POST", "/api/internal/v1/catalog/sync")
      .existingSecurity("GET", "/error")
      .build();
}
```

O ID recebido seleciona o recurso. O serviço resolve o proprietário com sua
fonte autoritativa e pode lançar `ResponseStatusException(NOT_FOUND)` para
ocultar um recurso. A criação pode usar o ator autenticado como proprietário.
Para cada handler MVC, declare a política de produto ou delegue o método e
padrão exatos para a segurança existente, inclusive handlers operacionais
como `/error` por método. Handler sem classificação falha com 503. Para operações
diferenciadas por parâmetros no mesmo método e padrão MVC, mantenha a mesma
ação ou dê a cada operação um caminho distinto. Servlets, rotas funcionais e
handlers de recursos fora de `HandlerMethod` precisam da proteção própria do
serviço. Preserve a chain explícita e seus controles de rota, método, CORS e
ownership.

Use `SESSION_VIEW` e `INVITE_SELF` para sessão mínima, emissão de código,
listagem e resposta aos próprios convites. Ambas exigem JWT humano e
proprietário igual ao ator. A avaliação admite as exceções restritas segundo
a política v1. A regularização, quando exposta, mantém sua política específica
de autenticação e ownership sob classificação exata. Operações técnicas,
administrativas e editoriais mantêm as regras das respectivas chains. HEAD
herda a classificação de GET quando não existe declaração própria. Preflight
segue a política de CORS do consumidor e o handler de preflight do MVC.

## Falha e revogação

Um JWT humano ausente ou inválido retorna 401. Scope ou cliente insuficiente
e decisão v1 negativa retornam 403. Uma falha S2S, timeout, corpo inválido,
versão incompatível, decisão expirada ou divergência de correlação retorna
503. Os motivos internos são omitidos da resposta pública de negação. O cliente reutiliza apenas o token técnico até a margem de renovação. Cada
operação recebe uma decisão nova. O
`X-Correlation-ID` validado acompanha a decisão e a resposta. Métricas usam
somente o resultado agregado e medem latência, falha de contrato e falha S2S.

Antes da transição final sensível, invoque
`ProductAccessAuthorization.recheck(request)` com a requisição original. A
reavaliação resolve ownership e consulta o avaliador novamente. O serviço
dono segue responsável por revalidar o estado e o ownership na escrita
transacional. Para leituras supervisionadas, o futuro consumidor deverá
observar também a janela de fatos autorizada do serviço proprietário.

## Migração e testes

1. Use a rota canônica no family-service. Provisione a credencial
   confidencial e a allowlist de ação/recurso de cada consumidor conforme o
   TechDocs de família. Valide a credencial antes da ativação.
2. Confira o inventário de handlers e o mapeamento com a chain e o gateway
   existentes. Prepare os handlers de sessão mínima e convites próprios antes
   de ativar os gates que dependam deles.
3. Ative por serviço com configuração completa, mantendo a chain anterior e
   a proteção de ownership. Valide negativas, revogações e queda do avaliador
   antes de encaminhar tráfego.

```bash
mvn -B -f platform/pom.xml -pl bookrush-security-spring-boot-starter -am test
python3 scripts/tests/test_product_access_contract.py
bash scripts/test-product-access-keycloak.sh
```

O harness usa JWTs assinados, dois consumidores MVC e um avaliador controlado
pelo teste. A última execução adiciona uma conta de serviço do Keycloak 25
descartável para provar emissão e uso do JWT S2S diante desse mesmo avaliador.

O cenário ponta a ponta com avaliador real executa com
`bash scripts/test-premium-family-e2e.sh`. O consumidor descartável chama
o starter com JWT humano real. O starter usa um JWT técnico separado para
consultar família, que resolve os fatos atuais do Keycloak, PostgreSQL e
subscription-service.
