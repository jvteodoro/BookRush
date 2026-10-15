# Pipelines por microserviço e por vertical

Para disparos automatizados autenticados, sincronização de jobs inline e
diagnóstico de erros de CSRF/checkout, consulte o
[`runbook do agente Jenkins`](jenkins-agent-runbook.md).

Para o fluxo completo de desenvolvimento, autorização, CORS, migrations,
containers e smoke tests, consulte o
[`guia de desenvolvimento e validação de serviços`](service-development-checklist.md).

O job `bookrush-deploy` continua sendo a validação integrada e o caminho de
deploy. Para reduzir o tempo de feedback, o Jenkins também cria pipelines
isoladas no bootstrap da instância.

## SeaweedFS

O job `bookrush-seaweedfs` é a pipeline dedicada da infraestrutura de
armazenamento. Ele executa o change gate, valida a configuração do Compose,
roda `scripts/test-storage.sh` com PostgreSQL e SeaweedFS descartáveis e
constrói a imagem customizada em `infrastructure/seaweedfs/`. O volume de
produção nunca é usado pelos testes.

`PUBLISH=true` publica a imagem como
`<REGISTRY>/bookrush/seaweedfs:<build>-<sha>`. `DEPLOY=true` é opt-in e
recria somente `seaweedfs`, `seaweedfs-oidc` e `reverse-proxy` no Compose de
produção, preservando o volume `seaweedfs_data`. Antes de alterar a instância,
o script exige que o SeaweedFS atual esteja saudável; se a nova versão falhar
no healthcheck, restaura a imagem anterior automaticamente.

Os parâmetros do job são `BRANCH`, `GIT_CREDENTIAL_ID`, `REGISTRY`,
`REGISTRY_CREDENTIAL_ID`, `PUBLISH` e `DEPLOY`. A publicação e o deploy ficam
desativados por padrão. O deploy usa `BOOKRUSH_REPO_ROOT` e
`BOOKRUSH_ENV_FILE` do ambiente protegido do Jenkins, normalmente
`/opt/bookrush` e `/run/bookrush.env`.

## Microserviços

Cada job `bookrush-microservice-<serviço>` executa o `change gate`, compila o
serviço no Docker Maven/Java 21, roda `mvn verify` e constrói a imagem desse
serviço. Os jobs cobrem `catalog-service`, `book-ingestion-service`,
`book-analytics-service`, `book-content-service`, `reader-state-service`,
`reader-profile-service`, `reader-bff-service`, `social-service`,
`behavior-service`, `recommendation-service`, `publisher-service` e
`admin-service`.

O parâmetro `SERVICE` fica vazio nos jobs nomeados e é inferido pelo sufixo do
job. Também pode ser informado explicitamente ao usar o Jenkinsfile genérico
como Pipeline from SCM. A lista é fechada em `scripts/test-microservice.sh`;
valores desconhecidos falham antes de qualquer build.

## Verticais

Cada job `bookrush-vertical-<vertical>` executa a mesma pipeline, validando a
composição funcional correspondente:

| Job | Escopo |
| --- | --- |
| `bookrush-vertical-catalog` | catálogo e conteúdo |
| `bookrush-vertical-ingestion` | ingestão e catálogo, incluindo bibliográfico |
| `bookrush-vertical-reader` | BFF, estado, perfil, recomendações e conteúdo |
| `bookrush-vertical-social` | social, comportamento e perfil |
| `bookrush-vertical-analytics` | analytics e recomendações, incluindo E2E offline |
| `bookrush-vertical-publishing` | publisher e administração |
| `bookrush-vertical-platform` | todos os serviços, contratos e Keycloak |

As matrizes ficam em `scripts/test-vertical.sh`, permitindo a mesma execução
local e impedindo que um parâmetro execute caminhos arbitrários. As verticais
`catalog` e `ingestion` também executam o teste bibliográfico; `analytics`
executa o E2E offline; `platform` executa contratos e reconciliação descartável
do Keycloak.

O harness bibliográfico valida `docs/database/queries.sql` antes de iniciar o
Compose. O Dockerfile do catálogo copia o fixture para a imagem de testes, em
vez de usar bind mount; isso funciona também quando o Jenkins usa um daemon
Docker fora do container do agente.

As pipelines isoladas aceitam `PUBLISH`, `DEPLOY` e `DEPLOY_TARGET`. O bootstrap
do Jenkins reconcilia esses parâmetros também nos jobs já existentes; portanto,
após atualizar a imagem do Jenkins, os controles de deploy aparecem sem recriar
o job nem perder histórico ou credenciais. Compose
usa a imagem construída localmente e recria somente o serviço selecionado; o
deploy Kubernetes exige `PUBLISH=true`, `REGISTRY` e `KUBECONFIG_CREDENTIAL_ID`.
Uma pipeline vertical publica e implanta todos os serviços da sua matriz. O
deploy individual preserva rollback somente no Compose; no Kubernetes o
rollout falho deixa o Deployment disponível para diagnóstico e o Jenkins
marca a execução como falha.

## Arquivos e operação

- `infrastructure/jenkins/microservice.Jenkinsfile`: pipeline reutilizável por serviço.
- `infrastructure/jenkins/vertical.Jenkinsfile`: pipeline reutilizável por vertical.
- `infrastructure/jenkins/seaweedfs.Jenkinsfile`: pipeline dedicada do storage.
- `infrastructure/jenkins/deploy-seaweedfs.sh`: deploy isolado com healthcheck e rollback.
- `infrastructure/jenkins/init.groovy.d/bookrush.groovy`: cria/atualiza os 12 + 7 jobs e o job SeaweedFS.
- `infrastructure/jenkins/Dockerfile`: embarca os Jenkinsfiles no bootstrap.

O job `bookrush-admin-web` aceita `BRANCH`, `PUBLISH`, `REGISTRY`, a credential
do registry e `DEPLOY`. O estágio `Build, testes e lint` roda dentro do
`Dockerfile` do `admin-web`: `format:check`, `npm test`, `test:coverage`, build e
lint. O job então cria a imagem versionada
`bookrush/admin-web:<build>-<commit>`. Com `PUBLISH=true` e `REGISTRY`
preenchido, autentica e executa `docker push` dessa mesma tag. Quando
`DEPLOY=true`, passa essa tag explicitamente ao Compose e usa `--no-build`,
garantindo que o container iniciado seja a imagem validada.

A definição usa blocos declarativos separados para `when` e `steps`, portanto
deve ser validada com `scripts/validate-pipelines.sh` antes de ser publicada no
Jenkins. Como o job é criado como Pipeline inline, seu estágio de checkout usa
explicitamente o repositório e a branch informada; ele não depende de
`checkout scm` de Multibranch. O agente Jenkins e o daemon Docker podem ter
filesystems diferentes; por isso todas as verificações rodam no contexto
enviado pelo `docker build`.

O `frontend/Dockerfile` aplica a mesma sequência de validação. Assim, a
pipeline `bookrush-deploy`, que constrói a imagem principal, não depende apenas
do `test-portal.sh` do Backstage para validar o frontend.

Publicação e deploy são opt-in nas pipelines isoladas. Para publicar, use
`PUBLISH=true`, `REGISTRY` e a credential correspondente. Para Compose, use
`DEPLOY=true` e `DEPLOY_TARGET=compose`; para Kubernetes, também é obrigatório
publicar no registry e fornecer o kubeconfig. O `bookrush-deploy` continua
sendo o caminho integrado para atualizar catálogo,
ingestão, analytics, perfil do leitor e frontend juntos. A imagem
`READER_PROFILE_IMAGE` é obrigatória no contrato do deploy Compose e deve ser
gerada e publicada pela mesma execução que atualiza os demais serviços.

Em uma instalação existente, é necessário reconstruir/recriar somente o
container Jenkins para que o `init.groovy.d` e os Jenkinsfiles novos sejam
carregados; o volume `jenkins_home` é preservado. Os jobs existentes não são
apagados nem têm histórico alterado.
