# Guia de desenvolvimento, integração e deploy de serviços

Este guia é o procedimento obrigatório para criar ou alterar um serviço,
endpoint, cliente Keycloak, pipeline ou integração do BookRush. Ele existe para
evitar que uma alteração seja considerada pronta apenas porque compilou
localmente. A conclusão exige evidência no serviço real, através do mesmo
gateway, origem e token usados pelo cliente.

## 1. Defina o contrato antes do código

1. Identifique o serviço dono do dado e da rota. Não implemente uma operação
   administrativa no frontend, no Backstage ou em outro serviço apenas porque
   ele já possui uma API semelhante.
2. Atualize o OpenAPI, o controller, a documentação TechDocs e os testes na
   mesma alteração.
3. Para cada resposta HTTP, valide o schema OpenAPI. Respostas como `404` não
   podem conter uma mensagem solta como propriedade YAML; use `description`,
   `content` ou um `$ref` válido.
4. Registre método, caminho exato, parâmetros, status de sucesso, erros,
   audience, scopes/roles, origem CORS e serviço que realmente atende a rota.
5. Confirme o roteamento no reverse proxy. O caminho público pode ser diferente
   do caminho interno e não deve ser deduzido apenas pelo nome do serviço.

Critério de saída: OpenAPI validado, rota encontrada no proxy e um teste de
controller cobrindo sucesso e os principais erros.

## 2. Modele banco e migrations com PostgreSQL real

1. Crie uma migration nova e imutável; nunca edite uma migration já aplicada.
2. Execute a migration contra PostgreSQL da mesma versão usada no Compose.
3. Para índices de expressão, todas as funções precisam ser `IMMUTABLE`.
   `concat_ws(...)` pode impedir um índice de `to_tsvector`; prefira uma
   expressão imutável explícita ou uma coluna/materialized projection mantida
   pela aplicação.
4. Teste boot em banco vazio, upgrade a partir de versões intermediárias,
   validação repetida e o mapeamento JPA/JDBC.
5. Para `timestamptz`, leia `OffsetDateTime`/`ZonedDateTime` e converta para
   `Instant` na aplicação. Não assuma que o driver aceita `getObject(...,
   Instant.class)`.
6. Não use banco, bucket ou volume de produção nos testes. Fixtures devem ser
   criadas no ambiente descartável.

Comandos mínimos:

```bash
docker build --target build -t bookrush/catalog-service-test services/catalog-service
docker run --rm bookrush/catalog-service-test mvn -B test
bash scripts/test-storage.sh
```

Critério de saída: `mvn verify`/testes de integração verdes e migration
validada em banco vazio e em upgrade.

## 3. Configure identidade e autorização no Keycloak

Para cada nova rota, escreva primeiro uma matriz de autorização:

| Item | Pergunta obrigatória |
| --- | --- |
| Realm | É produto (`bookrush`) ou plataforma/admin (`bookrush-platform`)? |
| Issuer | O serviço valida exatamente o issuer do realm correto? |
| JWKS | A URL de chaves é confiável e acessível pelo container? |
| Audience | O token contém a audience esperada pelo serviço? |
| Scope/role | O método exige o scope/role mínimo necessário? |
| Cliente | O cliente humano ou S2S recebe essa permissão? |
| Expiração | O teste usa token novo, não um bearer expirado ou copiado parcialmente? |

Regras:

1. `401` significa token ausente, inválido, expirado, issuer/audience
   incorretos. `403` significa token válido sem autorização ou requisição
   bloqueada por política de origem/CORS.
2. Não remova autenticação para fazer o teste passar. Thumbnails, aprovação,
   assets e APIs administrativas continuam protegidos.
3. Diferencie `aud` de `azp`: o cliente que solicitou o token não substitui a
   audience do serviço.
4. Adicione scopes e audience mappers de forma declarativa nos arquivos do
   realm e aplique reconcile idempotente. Não apague usuários, sessões,
   volumes ou credenciais existentes.
5. Para S2S, use cliente próprio, scope mínimo e segredo no ambiente protegido.
   Nunca coloque token, senha ou client secret em Git, beads, logs ou argumentos
   visíveis.
6. Depois de mudar roles/scopes/mappers, gere token novo. Tokens antigos podem
   continuar sem as claims novas até expirarem.

Validações:

```bash
bash scripts/test-keycloak-realms.sh
bash infrastructure/keycloak/reconcile.sh --env-file .env
```

No teste real, decodifique apenas claims não sensíveis (`iss`, `aud`, `azp`,
`scope`, `exp`) e confirme que o serviço recebe as authorities esperadas.

## 4. Configure CORS junto com a API

CORS é parte do contrato da API, não um ajuste opcional do frontend.

1. Liste todas as origens reais que fazem chamadas no navegador: frontend,
   admin web, Backstage/TechDocs e hosts locais necessários.
2. Adicione somente origens explícitas em `CATALOG_CORS_ALLOWED_ORIGINS` ou na
   propriedade equivalente. Não use `*` com APIs autenticadas.
3. Permita os métodos usados (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`,
   `OPTIONS`) e os cabeçalhos reais (`Authorization`, `Content-Type`,
   `X-Admin-Client`, `Idempotency-Key`, etc.).
4. Registre a configuração para todas as rotas que o browser acessa, inclusive
   `/api/admin/**`, e não somente `/api/internal/**`.
5. Teste preflight com a origem real. Um `OPTIONS` rejeitado pode aparecer no
   frontend como `NetworkError`, 403 ou parecer um problema de autorização.

Exemplo de verificação:

```bash
curl -i -X OPTIONS 'https://bookrush.example/api/admin/books/<id>/approve' \
  -H 'Origin: https://admin.example' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: authorization,x-admin-client'
```

Critério de saída: resposta `2xx` com `Access-Control-Allow-Origin` exatamente
igual à origem enviada, sem abrir origem curinga.

## 5. Implemente o frontend com o cliente de API correto

1. Reutilize o cliente autenticado (`authClient.fetch`/equivalente) para toda
   API protegida. `img src` e `fetch` cru não carregam automaticamente bearer
   em uma SPA.
2. Para imagens protegidas, busque o blob com autenticação, crie uma Object
   URL e revogue-a no cleanup. Não coloque bearer na URL nem em logs.
3. Diferencie estado `loading`, `401`, `403`, `404` e imagem ausente. Um livro
   sem thumbnail deve ter fallback visual, não uma requisição infinita.
4. Após criar uma rota administrativa, atualize permissões do cliente do admin,
   tela, tratamento de erro e teste de integração.
5. Verifique que reimportação é idempotente: livro já importado não deve ser
   apresentado como novo nem gerar duplicata; reprocessamento de thumbnail deve
   atualizar apenas o que está ausente ou falho.

Critério de saída: build do frontend verde e teste no navegador com sessão
real, origem real e DevTools confirmando a chamada autenticada.

## 6. Prepare Docker e Compose

1. Atualize Dockerfile, contexto, `.dockerignore`, variáveis e healthcheck do
   serviço. O Dockerfile deve copiar todos os arquivos usados por build,
   migrations, fixtures e documentação de validação.
2. Atualize `infrastructure/compose.yaml` e `.env.example` juntos. Toda nova
   variável deve ter nome, default seguro e documentação; valores de produção
   permanecem somente no `.env` protegido.
3. Confirme que a imagem usada no deploy é a imagem recém-construída. Não
   conclua pelo `docker build` se o container ainda está usando digest antigo.
4. Confira `depends_on`, rede, DNS interno, portas, volumes persistentes,
   buckets, permissões e healthchecks. SeaweedFS deve ser validado em volume
   descartável e produção deve preservar `seaweedfs_data`.
5. Para deploy parcial, todas as imagens exigidas pelo Compose devem existir.
   O erro `READER_PROFILE_IMAGE: Imagem ... ausente` indica contrato de imagem
   incompleto, não problema do microserviço isolado.

Verificação no host de deploy:

```bash
docker compose --env-file /run/bookrush.env -f infrastructure/compose.yaml \
  config --quiet
docker compose --env-file /run/bookrush.env -f infrastructure/compose.yaml \
  ps
docker inspect bookrush-<service>-1 \
  --format 'image={{.Image}} status={{.State.Status}} health={{.State.Health.Status}}'
```

Use o caminho de ambiente efetivamente configurado no host; não substitua
credenciais por `.env.example`.

## 7. Atualize e teste a pipeline Jenkins

1. Faça push da branch antes do disparo. Jenkins não executa mudanças que só
   existem no workspace local.
2. Mantenha `Jenkinsfile` e `infrastructure/jenkins/bookrush.Jenkinsfile`
   idênticos quando ambos forem contratos do pipeline.
3. Valide shell e contratos antes do Jenkins:

```bash
bash -n scripts/*.sh infrastructure/jenkins/*.sh
bash scripts/validate-pipelines.sh
bash scripts/validate-change.sh --ci
```

4. O job integrado `bookrush-deploy` usa Pipeline from SCM e carrega o
   `Jenkinsfile` da branch informada no parâmetro `BRANCH`. Jobs de serviço e
   verticais ainda podem ser inline; se um deles for alterado, a definição
   persistida precisa ser sincronizada pelo bootstrap. Não apague o job ou o
   volume para “corrigir” sincronização.
5. Faça login OIDC com usuário permanente do agente, valide `whoAmI`, obtenha
   crumb da mesma sessão e só então chame `buildWithParameters`. Não reutilize
   callback, cookie ou crumb antigo.
6. Diferencie os objetivos:
   - microserviço: testes e imagem; deploy só com `DEPLOY=true`;
   - vertical: matriz de serviços e deploy da matriz;
   - `bookrush-deploy`: validação integrada e Compose completo;
   - `admin-web`: frontend administrativo separado;
   - SeaweedFS: pipeline própria, com volume persistente e rollback.
7. `PUBLISH=true` apenas publica imagem. Para atualizar Compose é necessário
   `DEPLOY=true` e o target correto. Kubernetes também exige registry e
   kubeconfig.
8. Acompanhe o build até `building=false` e `result=SUCCESS`; depois valide o
   container efetivo. Não considere “imagem publicada” como “serviço
   implantado”.

## 8. Faça smoke test pelo caminho público

O teste final deve reproduzir o cliente, não apenas chamar `localhost` dentro do
container. Use token novo obtido do Keycloak e não registre seu conteúdo.

Para uma rota protegida, valide nesta ordem:

1. sem token: deve retornar `401`;
2. token inválido/expirado: `401`;
3. token válido sem scope: `403`;
4. token válido com audience, scope e origem corretos: status de negócio
   esperado (`200`, `201`, `204`, `404` ou `409` conforme o estado do dado);
5. preflight da mesma origem: `2xx` e cabeçalhos CORS corretos;
6. logs do serviço sem stack trace inesperado;
7. healthcheck e imagem/digest do container atualizados.

Para rotas de thumbnail, confirme também que o livro possui asset aprovado,
versão física disponível e objeto no bucket correto. `401/403` indicam
autenticação/autorização/origem; `404` depois de autenticação normalmente indica
ausência de asset aprovado, chave errada ou serviço/container sem a migração e
imagem corretos.

## 9. Checklist de entrega

- [ ] Contrato OpenAPI, controller, documentação e testes atualizados.
- [ ] Migration nova validada em banco vazio e upgrade.
- [ ] Testes JPA/JDBC cobrem tipos PostgreSQL reais (`timestamptz`, UUID,
      JSON e enums).
- [ ] Issuer, JWKS, audience, scopes, roles, cliente e claims conferidos.
- [ ] Keycloak reconciliado sem apagar usuários, sessões ou volumes.
- [ ] CORS cobre somente as origens reais e inclui preflight da rota.
- [ ] Frontend usa cliente autenticado e trata todos os estados HTTP.
- [ ] Dockerfile, Compose, `.env.example`, healthcheck e dependências
      atualizados.
- [ ] `bash -n`, change gate, pipeline validation e testes do serviço passaram.
- [ ] Branch foi publicada e Jenkins executou a revisão correta.
- [ ] Imagem foi publicada e/ou implantada conforme o parâmetro solicitado.
- [ ] Container efetivo está saudável e usa o digest esperado.
- [ ] Smoke test público foi executado com token novo e origem real.
- [ ] Evidências registradas sem tokens, cookies, senhas ou secrets.
- [ ] Só então a tarefa pode ser marcada como concluída.

## Histórico das falhas que motivaram este guia

As ocorrências desta implementação mostraram por que cada etapa é necessária:

- resposta OpenAPI `404` inválida parou a validação antes do runtime;
- índice de busca com `concat_ws` não imutável derrubou o Flyway e todos os
  testes de contexto;
- tokens com `aud`/scope incorretos produziram 401/403 nas APIs administrativas;
- ausência da origem do admin no CORS produziu `Invalid CORS request`/403 mesmo
  com token válido;
- a rota de thumbnail atravessou cadeias de segurança diferentes e exigiu
  configuração explícita do realm de produto, audience e cliente autenticado;
- `timestamptz` mapeado diretamente para `Instant` causou 500 na fila de review;
- imagem ausente no contrato Compose impediu o deploy (`READER_PROFILE_IMAGE`);
- pipelines isoladas inicialmente publicavam imagem sem necessariamente
  recriar o serviço;
- jobs Jenkins inline ficaram com definições antigas e disparos sem sessão/crumb;
- sucesso do build local não significou deploy do digest correto nem teste pelo
  gateway público.

O princípio operacional é: cada correção precisa de uma evidência correspondente
no mesmo nível em que o erro pode ocorrer — contrato, banco, identidade, origem,
imagem, container, gateway e cliente.
