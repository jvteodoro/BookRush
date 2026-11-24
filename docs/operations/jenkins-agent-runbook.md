# Disparo de pipelines Jenkins pelo agente

Este runbook descreve o fluxo usado por um agente automatizado para executar
pipelines no Jenkins protegido por Keycloak. Ele evita os erros recorrentes de
autenticação, CSRF, jobs inline desatualizados e parâmetros ainda não
materializados.

## Pré-condições

O change gate das pipelines integrada, Backstage, microserviço, vertical e
SeaweedFS executa `bash scripts/validate-change.sh --ci --offline` diretamente
no agente, com timeout de cinco minutos. Git e Python 3 já fazem parte da imagem
Jenkins; PyYAML e jsonschema são fornecidos por `python3-yaml` e
`python3-jsonschema` durante o build da imagem. O gate não baixa pacotes nem
inicia um container para validar o diff. Isso também evita trocar o dono do
workspace e precisar de uma exceção global de `safe.directory`.

1. Publique o commit na branch remota antes de disparar o job. O Jenkins não
   executa alterações que existem somente no checkout local.

Uma imagem antiga do agente pode continuar validando alterações que não exigem
analytics. Se PyYAML/jsonschema forem necessários e estiverem ausentes, o gate
falha imediatamente com instrução para atualizar a imagem; não tenta instalar
pacotes durante o job. A imagem atualizada deve ser construída/implantada em
janela controlada, preservando `jenkins_home`, jobs, sessões e credenciais.
Agentes customizados precisam fornecer Bash, Git, Python 3 e esses módulos.

O teste do runtime de modelos executa os probes via `docker exec` dentro do
container temporário. Isso evita assumir que o `127.0.0.1` do agente, o IP da
bridge Docker e o namespace do runtime são roteáveis entre si. Se o healthcheck
falhar, os logs do runtime são emitidos pelo próprio script para evitar
diagnóstico por timeout.
2. Confirme que `https://jenkins-bookrush.jteodoro.tec.br` resolve e responde.
   HTTP 403 sem sessão é esperado; falha de DNS ou HTTP 5xx indica problema de
   infraestrutura, não de pipeline.
3. Use o usuário permanente `codex-jenkins-agent` (ou outro usuário aprovado).
   Não apague, recrie ou resete a senha a cada execução. A credencial deve
   permanecer no Keycloak ou no secret manager do host, nunca em Git, logs ou
   argumentos exibidos.
4. O usuário precisa pertencer ao grupo Keycloak `platform-operators` para
   executar jobs aprovados. `platform-admins` só deve ser concedido quando o
   agente precisar sincronizar definições inline pelo Script Console.

5. O login OIDC do Jenkins depende de `postgres`, `keycloak` e `reverse-proxy`
   no Compose principal. Esses serviços usam `restart: unless-stopped` e devem
   permanecer saudáveis; não basta o container do Jenkins estar ativo. Após
   uma reinicialização do host, confirme os quatro serviços antes de investigar
   usuário, senha ou permissões.

## Ordem correta

### 1. Criar uma sessão OIDC nova

O agente deve iniciar uma autorização nova em:

```text
https://jenkins-bookrush.jteodoro.tec.br/securityRealm/commenceLogin
```

Mantenha o mesmo cookie jar durante todo o fluxo. O redirect contém um código
de uso único; não reutilize uma URL de callback antiga. Após o login, valide a
sessão antes de disparar builds:

```bash
curl -fsS -b "$COOKIE_JAR" \
  https://jenkins-bookrush.jteodoro.tec.br/whoAmI/api/json \
  | jq '{authenticated,name,authorities}'
```

O resultado precisa conter `authenticated: true` e o nome do usuário esperado.

### 2. Obter o crumb CSRF

Todo `POST` ao Jenkins deve usar o crumb da mesma sessão:

```bash
crumb_json=$(curl -fsS -b "$COOKIE_JAR" \
  https://jenkins-bookrush.jteodoro.tec.br/crumbIssuer/api/json)
crumb=$(jq -r .crumb <<<"$crumb_json")
crumb_field=$(jq -r .crumbRequestField <<<"$crumb_json")
```

Nunca copie o crumb entre sessões. `403` em `build` com sessão autenticada
normalmente significa crumb ausente, expirado ou cookie não enviado.

### 3. Escolher o job correto

| Objetivo | Job | Faz deploy? |
| --- | --- | --- |
| Validar/construir um serviço | `bookrush-microservice-<service>` | Com `DEPLOY=true` |
| Validar uma vertical | `bookrush-vertical-<vertical>` | Com `DEPLOY=true` |
| Atualizar o stack Compose | `bookrush-deploy` | Sim, com `DEPLOY=true` |
| Atualizar a console administrativa | `bookrush-admin-web` | Sim, com `DEPLOY=true` |

Exemplo para o serviço alterado:

```bash
curl -fsS -X POST \
  -b "$COOKIE_JAR" -H "$crumb_field: $crumb" \
  https://jenkins-bookrush.jteodoro.tec.br/job/bookrush-microservice-book-ingestion-service/buildWithParameters \
  --data-urlencode BRANCH=main
```

Para atualizar a aplicação integrada no Compose:

```bash
curl -fsS -X POST \
  -b "$COOKIE_JAR" -H "$crumb_field: $crumb" \
  https://jenkins-bookrush.jteodoro.tec.br/job/bookrush-deploy/buildWithParameters \
  --data-urlencode BRANCH=main \
  --data-urlencode DEPLOY=true \
  --data-urlencode DEPLOY_TARGET=compose \
  --data-urlencode RUN_GUTENBERG_IMPORT=false
```

Para o `admin-web`, use os parâmetros `BRANCH=main` e `DEPLOY=true`. Esse job
é separado do `bookrush-deploy`; executar somente o deploy principal não
recria automaticamente o `admin-web`.

O bootstrap Jenkins mantém a definição de parâmetros do job `bookrush-deploy`
sincronizada com o `Jenkinsfile` principal, inclusive `RUN_GUTENBERG_IMPORT`,
`GUTENBERG_DRY_RUN`, `GUTENBERG_EXTERNAL_IDS`, `INGESTION_BASE_URL`,
`INGESTION_CREDENTIAL_ID` e `GUTENBERG_TIMEOUT_MINUTES`. Ao atualizar o
bootstrap, reinicie/recarregue a configuração do Jenkins conforme o runbook e
confirme que o job expõe os mesmos parâmetros antes de disparar uma execução.

Nos jobs isolados, use `DEPLOY_TARGET=compose` para recriar somente o
microserviço ou todos os serviços da vertical. Para Kubernetes, use
`DEPLOY_TARGET=kubernetes`, `PUBLISH=true`, `REGISTRY` e
`KUBECONFIG_CREDENTIAL_ID`; a imagem precisa estar acessível pelo cluster.

### Smoke automatizado do reader BFF

O job `bookrush-microservice-reader-bff-service` possui o parâmetro opt-in
`RUN_PUBLIC_E2E`. Quando marcado, ele exige `DEPLOY=true`, `E2E_BOOK_ID` e a
credential Jenkins `E2E_CREDENTIAL_ID` do tipo username/password para um
usuário de teste do realm de produto `bookrush`. O estágio executa
`scripts/test-reader-bff-e2e.sh`, que faz Authorization Code + PKCE sem
interação manual, valida claims não sensíveis (`iss`, `aud`, `scope`, `exp`),
renova o access token com refresh token, exercita respostas 401/403/200,
verifica que um token do realm de produto não entra na administração do realm
de plataforma, o manifesto EPUB e o preflight CORS. Senhas,
tokens, cookies e corpos de resposta nunca são impressos.

O mesmo smoke percorre a estrutura do livro (`book`/`chapters`), operações
sociais autenticadas de teste (like, contagem, share, comentário e unlike),
um evento autenticado no behavior-service e logout OIDC usando o
`id_token_hint` apenas em memória. O `E2E_BOOK_ID` deve existir no catálogo;
os registros criados são marcados como teste de integração.

Para validar integrações técnicas da ingestão, o job `bookrush-deploy` aceita
`RUN_INGESTION_S2S_E2E=true` junto com `DEPLOY=true` e
`DEPLOY_TARGET=compose`. O estágio usa a credential Secret text
`bookrush-ingestion-token` como segredo do client `bookrush-ingestion-admin`,
confere `aud=bookrush-ingestion` sem registrar o token e chama somente
`POST /api/admin/v1/ingestion/run` com `dryRun=true`, `processAssets=false` e
uma chave de idempotência nova. O resultado esperado é HTTP 202 (ou 200 em
replay); nenhum livro, asset ou fonte externa é importado. O service account
desse client deve possuir a role `platform-operator` no realm
`bookrush-platform`; audience sem role resulta em HTTP 403. O job individual de
`book-ingestion-service` oferece o mesmo teste com os parâmetros
`RUN_INGESTION_S2S_E2E`, `INGESTION_S2S_CREDENTIAL_ID` e
`INGESTION_S2S_BASE_URL`.

Quando `DEPLOY=true` e `DEPLOY_TARGET=compose`, a pipeline executa antes da
ingestão o reconcile declarativo do Keycloak usando `/run/bookrush.env`, o
mesmo arquivo protegido do deploy, através de containers que compartilham o
volume do workspace do agente. Isso é necessário porque o realm persistente
não reaplica automaticamente alterações de roles durante a recriação das
imagens; o estágio não é executado em builds de validação sem deploy.

O reconcile executado pelo agente injeta somente a credencial S2S de ingestão
que pertence à pipeline. O segredo já provisionado do client
`bookrush-admin-service` permanece no volume persistente do Keycloak e não é
rotacionado nem exigido pelo job. Se esse client precisar de rotação, ela deve
ser uma operação explícita de manutenção, com atualização coordenada do
ambiente protegido e dos serviços consumidores; nunca deve ser criada como
parâmetro implícito do deploy.

O estágio de contratos também executa `scripts/test-keycloak-jwks-rotation.sh`
em um Keycloak descartável. Ele cria um provider `rsa-generated` de prioridade
maior pelo Admin API, confirma a troca do `kid` e verifica que o JWKS continua
publicado. O teste não usa o volume `keycloak_data` persistente.

O usuário de teste precisa estar no realm `bookrush`, ter o cliente
`bookrush-web` e o scope `bookrush.catalog.read`; o usuário administrativo do
Jenkins no realm `bookrush-platform` não substitui essa credencial. O parâmetro
fica desmarcado por padrão para que builds normais não dependam de uma conta
humana. A ausência da credential deve falhar somente quando o opt-in for usado.

As credenciais locais de operação devem ser mantidas fora do Git, por exemplo
em um arquivo do host com permissão `0600`. Para o Jenkins, o administrador da
instância deve cadastrar o mesmo par como uma credential global do tipo
`Username with password`, com o ID `bookrush-product-e2e`; o usuário
`platform-operators` pode executar o job, mas não deve receber permissão de
criar ou ler credentials. Verifique a existência pelo job antes de habilitar
`RUN_PUBLIC_E2E`; se o login falhar, o harness informa separadamente rejeição
de username/password e ausência de callback OIDC.

### 4. Acompanhar fila e build

O `POST` bem-sucedido retorna `201` e um header `Location` apontando para
`/queue/item/<id>/`. Não dispare novamente só porque o job ainda está na fila.
Consulte a fila até aparecer `executable.number`:

```bash
curl -fsS -b "$COOKIE_JAR" \
  https://jenkins-bookrush.jteodoro.tec.br/queue/api/json \
  | jq '.items[] | {id, task: .task.name, executable: .executable.number}'
```

Depois acompanhe o build:

```bash
curl -fsS -b "$COOKIE_JAR" \
  https://jenkins-bookrush.jteodoro.tec.br/job/<job>/<build>/api/json \
  | jq '{number,building,result,duration}'
```

Só considere concluído quando `building=false` e `result=SUCCESS`. Em caso de
falha, leia `consoleText` do mesmo build antes de repetir.

## Definição e parâmetros da pipeline

O job `bookrush-deploy` usa `CpsScmFlowDefinition` com o repositório BookRush,
branch `*/${BRANCH}` e Script Path `Jenkinsfile`. O parâmetro BRANCH seleciona
ambos a definição e o código da revisão; mantenha a feature atualizada com main.
Fixar a definição em main fazia o Jenkins executar etapas antigas mesmo depois
de um checkout da feature. O script idempotente
`infrastructure/jenkins/reconcile-publishing-pipeline.groovy` pode ser executado
na Script Console autenticada quando não houver build ativo. Ele preserva
credenciais, parâmetros, histórico e volumes e não dispara deploy.

No startup, `infrastructure/jenkins/init.groovy.d/bookrush.groovy` reconcilia
essa definição SCM e `ParametersDefinitionProperty`, incluindo
`RUN_INFRA_CONTRACTS`. O gate `scripts/validate-pipelines.sh` falha se a opção
existir no Jenkinsfile mas faltar no bootstrap (ou vice-versa). Após publicar
mudanças no bootstrap, aguarde não haver builds ativos e recrie somente o
container Jenkins com `docker compose ... up -d --no-deps --force-recreate
jenkins`; o Compose preserva o job, o histórico e o volume `jenkins_home`.
Confirme a definição e os parâmetros pela API/UI antes do próximo deploy.

O bootstrap também deve atualizar a `ParametersDefinitionProperty` do
`bookrush-admin-web` sempre que o Jenkinsfile ganhar parâmetros novos. Depois
de uma alteração em `admin-web.Jenkinsfile`, confirme no endpoint
`/job/bookrush-admin-web/api/json` que `PUBLISH`, `REGISTRY` e
`REGISTRY_CREDENTIAL_ID` aparecem antes de disparar uma publicação; um job com
somente `BRANCH`/`DEPLOY` ainda está usando a definição antiga.

Quando a sincronização pelo Script Console for indispensável, fixe a URL no
commit publicado, confirme o hash retornado e só então execute o build. Não
busque `main` sem identificar a revisão, pois isso dificulta reproduzir o
resultado.

## Erros recorrentes

| Sintoma | Causa provável | Correção |
| --- | --- | --- |
| `403` e `Permission denied` | crumb ausente ou sessão sem permissão | renovar sessão, obter crumb e validar `whoAmI` |
| `400` em `buildWithParameters` | job recém-criado ainda não carregou `parameters` | executar uma primeira vez sem parâmetros; depois usar `buildWithParameters` |
| `checkout scm is only available` | job inline usando checkout de Multibranch | usar `GitSCM` explícito com `BRANCH` |
| `npm: not found` | agente não tem Node instalado | executar build dentro do Dockerfile/container Node |
| `npm ci` sem lockfile | workspace/contexto não contém `package-lock.json` | validar o checkout; usar o comando definido pela pipeline |
| `No such file or directory /app/package.json` | bind mount do workspace do container Jenkins não existe no daemon Docker | usar `docker build` com contexto, não bind mount |
| `Validação Mobile` encontra `/workspace/mobile` vazio ou deixa arquivos root-owned | bind mount do caminho do agente não é visível para o daemon Docker, ou o Node roda como root no volume compartilhado | usar `--volumes-from "$HOSTNAME" -w "$PWD/mobile" --user "$(id -u):$(id -g)"`; antes de `deleteDir`, corrigir a posse somente de `mobile/node_modules` e `mobile/dist` gerados pelo gate |
| `Bad substitution` | sintaxe Bash executada por `/bin/sh` | usar expansão POSIX ou `bash -lc` explicitamente |
| build mostra Jenkinsfile antigo | definição inline não foi sincronizada | atualizar a definição do job e confirmar `config.xml`/console |
| `invalid_code` no Keycloak | callback OIDC reutilizado | iniciar uma autorização nova; não repetir o callback antigo |
| `Well known configuration could not be loaded` ou `HTTP 502` no discovery | Keycloak ou reverse proxy fora do ar | confirmar `docker compose ps`, restaurar somente `postgres`, `keycloak` e `reverse-proxy`, aguardar os healthchecks e repetir o redirect OIDC |

## Evidência mínima para o relatório

Registre somente dados não sensíveis: commit, job, número do build, parâmetros
não secretos, resultado e URL do build. Não registre senha, client secret,
Bearer token, crumb, cookie, authorization code ou conteúdo de `.env`.

Uma atualização de produção só deve ser reportada como concluída quando o
build de validação, o build de deploy e o health check do Compose estiverem
verdes.

## Change gate e falha de durable task

Os avisos de `debconf` sobre TTY não demonstram falha do gate: ele pode mudar
para o modo noninteractive. O problema do caminho antigo era executar
`apt-get`/`pip` silenciosos e dependentes da rede antes da validação em todo build.
O caminho offline os elimina e registra o início da validação; erros de leitura
do diff Git também falham explicitamente, em vez de aparentar ausência de mudanças.

`wrapper script does not appear to be touching the log file` indica que o
heartbeat do processo durable não foi observado. Um log de console isolado não
prova se houve OOM, reinício/desconexão do agente ou problema de filesystem.
Se persistir com o gate nativo, preserve os logs e confira no host o estado do
agente, `OOMKilled`, eventos/reinícios do container, espaço/inodes do volume e
logs do kernel no intervalo da falha. Não altere HEARTBEAT_CHECK_INTERVAL nem
reinicie/apague o volume apenas para esconder a mensagem.

Validação reproduzível: `python3 scripts/test-change-gate.py`,
`bash scripts/validate-pipelines.sh` e
`bash scripts/validate-change.sh --ci --offline`. Os testes usam repositórios Git
descartáveis e bloqueiam Docker/apt/pip para garantir que ausência de dependências
falhe rápido e que regras de documentação/analytics continuem sendo executadas.
