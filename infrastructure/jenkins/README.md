# Jenkins BookRush

O job `bookrush-deploy` é criado no primeiro boot. O Jenkins mantém o assistente
inicial e a autenticação; não há acesso anônimo ao deploy. Somente usuários
confiáveis devem ter acesso ao job, pois ele usa o Docker do servidor.

## Instalação no servidor

Configure `DOCKER_GID` no `.env` com `stat -c '%g' /var/run/docker.sock`.
O `.env` deve ser legível pelo UID 1000 do container Jenkins (padrão da imagem).

```bash
docker compose -f infrastructure/compose.yaml --env-file .env --profile ci up -d --build --no-deps jenkins
sudo bash infrastructure/nginx/install-jenkins.sh
sudo certbot --nginx --redirect -d jenkins-bookrush.jteodoro.tec.br
```

O DNS `jenkins-bookrush` deve apontar para este servidor. No Cloudflare, use
Full (strict) após emitir o certificado. O nome está no primeiro nível de
subdomínios, coberto pelo Universal SSL da zona `jteodoro.tec.br`.

Acesse `https://jenkins-bookrush.jteodoro.tec.br` e conclua o assistente.
Para obter a senha inicial, execute uma única vez no terminal do servidor:

```bash
docker compose -f infrastructure/compose.yaml --env-file .env exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Crie o administrador e mantenha a URL Jenkins como
`https://jenkins-bookrush.jteodoro.tec.br/`. Os plugins necessários já vêm
instalados na imagem.

### OIDC administrativo (opt-in)

O plugin OIDC, JCasC e Role Strategy são incluídos com versões fixadas, mas a
autenticação OIDC não é ativada automaticamente para não bloquear uma instância
existente. Depois de criar o client `bookrush-jenkins` no realm
`bookrush-platform`, forneça os valores somente no ambiente protegido:

```dotenv
CASC_JENKINS_CONFIG=/opt/bookrush/casc/jenkins-oidc.yaml
JENKINS_OIDC_CLIENT_ID=bookrush-jenkins
JENKINS_OIDC_CLIENT_SECRET=<segredo-do-client>
JENKINS_OIDC_WELL_KNOWN_URL=https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush-platform/.well-known/openid-configuration
```

Recrie o container Jenkins em uma janela controlada e mantenha uma sessão
administrativa de recuperação. O arquivo JCasC é somente leitura e não contém
segredo. Os grupos `platform-admins`, `platform-operators` e
`platform-developers` são concedidos no Keycloak; desenvolvimento não concede
administração. Valide primeiro em um projeto Compose descartável e confirme
que jobs, credenciais e histórico continuam no volume `jenkins_home`.

O client usa os mappers explícitos `preferred_username` e `groups` declarados
em `infrastructure/keycloak/bookrush-platform-realm.json`; o JCasC seleciona
`preferred_username` como nome da conta e solicita somente `openid`. O
procedimento completo, incluindo o diagnóstico de `invalid_code` e usuário
vazio quando um callback é repetido, está em
[`docs/auth/jenkins-oidc.md`](../../docs/auth/jenkins-oidc.md).

## Build e deploy pelo navegador

Para execução automatizada pelo agente, incluindo autenticação OIDC, crumb
CSRF, fila, jobs inline e diagnóstico, use o
[`runbook do agente Jenkins`](../../docs/operations/jenkins-agent-runbook.md).

1. Abra **bookrush-deploy → Build with Parameters**.
2. Informe **BRANCH** (nome exato, como `main` ou `feature/catalogo`).
3. Use **DEPLOY_TARGET=compose** para este servidor.
4. Marque **DEPLOY** para atualizar a aplicação; desmarcado apenas compila/testa.
5. Clique em **Build** e acompanhe **Console Output**.

O checkout usa o GitHub, portanto somente commits enviados para a branch
remota participam do build. Alterações locais deste diretório não são implantadas.
O SHA da revisão aparece na descrição do build e nas tags das imagens.
O backend é testado em um container Maven/Java 21; o frontend é compilado em
seu estágio Docker Node. Não é necessário instalar Maven/Node no Jenkins.
O estágio `Validação Mobile` executa `npm ci`, `npm run typecheck` e
`EXPO_NO_TELEMETRY=1 EXPO_NO_DOCTOR=1 npm run export` em `mobile/` usando
`node:20-bookworm`. O bundle Android em `mobile/dist/` é arquivado como
artefato; a dependência `expo-asset` fica fixada no lockfile para o export do
Expo SDK 52. Isso valida o aplicativo, mas não publica APK nem substitui um
job de distribuição mobile.

Antes dos builds, as pipelines executam `scripts/validate-change.sh --ci`.
Esse gate exige documentação ou contrato para mudanças de código, infraestrutura
e Backstage e valida a consistência dos Jenkinsfiles. A mesma regra pode ser
executada localmente após instalar os hooks com `bash scripts/install-hooks.sh`.

No job principal, `scripts/test-portal.sh --validation-only` executa contratos,
TechDocs, TypeScript, lint e testes sem exportar ou desempacotar uma imagem
Backstage que não será implantada por esse job. A imagem runtime fica a cargo
da pipeline dedicada do Backstage. Nela, o runtime mantém dependências como
somente leitura e concede escrita apenas a `/app/techdocs`, evitando um
`chown -R` demorado sobre toda a árvore de dependências.

O gate roda em um container Python efêmero. Como o agente Jenkins usa o socket
Docker e o workspace está no volume nomeado `jenkins_home`, a etapa usa
`--volumes-from "$HOSTNAME"` para compartilhar o workspace com o container do
gate, mantendo a chamada `bash scripts/validate-change.sh --ci`. Não use `-v "$PWD:/repo"` nesse agente: esse caminho é resolvido pelo
daemon Docker no host e resulta em um diretório vazio dentro do gate.

### Pipeline dedicada do Backstage

`backstage.Jenkinsfile` valida e empacota somente o Developer Portal. Cadastre
esse arquivo como um segundo job Jenkins multibranch ou Pipeline SCM para que a
documentação possa ser verificada sem reconstruir os microserviços. Por padrão,
ela faz checkout, executa `scripts/test-portal.sh` e cria uma imagem local;
`PUBLISH` e `DEPLOY` são opt-ins.

O Jenkinsfile inclui polling SCM a cada cinco minutos como fallback para
ambientes sem webhook. Assim, alterações em `catalog-info.yaml`, `api/`,
`docs/` ou no próprio portal regeneram o catálogo e o TechDocs. Em instalações
com webhook GitHub, prefira o webhook e mantenha o polling desativado no job
para evitar builds duplicados. O job só publica ou implanta quando os
parâmetros `PUBLISH`/`DEPLOY` estiverem habilitados.

Para publicar, informe `REGISTRY`, habilite `PUBLISH` e configure a credential
`REGISTRY_CREDENTIAL_ID`. Para atualizar o Compose do portal, habilite `DEPLOY`.
Por padrão o job usa `/run/bookrush.env`, que é montado no container Jenkins
pelo Compose principal; altere `BACKSTAGE_ENV_FILE` somente quando o agente usar
outro caminho. O job exige `BACKSTAGE_POSTGRES_PASSWORD`; quando ela não existe,
cria um arquivo temporário a partir de `POSTGRES_PASSWORD` e o remove ao sair.
O deploy usa a imagem gerada no próprio build e aguarda PostgreSQL e Backstage
ficarem saudáveis.

### Pipelines por microserviço e vertical

O bootstrap também cria jobs isolados para os 12 microserviços e para as sete
verticais funcionais. Eles usam os Jenkinsfiles reutilizáveis
`microservice.Jenkinsfile` e `vertical.Jenkinsfile`. Por padrão executam apenas
validação; com `PUBLISH=true` publicam as imagens e com `DEPLOY=true` fazem
deploy Compose ou Kubernetes conforme `DEPLOY_TARGET`. A matriz,
os nomes dos jobs e os comandos locais correspondentes estão em
[`docs/operations/jenkins-pipelines.md`](../../docs/operations/jenkins-pipelines.md).

### Pipeline dedicada do SeaweedFS

O bootstrap cria o job `bookrush-seaweedfs` a partir de
`seaweedfs.Jenkinsfile`. A execução normal apenas valida a infraestrutura,
executa `scripts/test-storage.sh` e constrói a imagem customizada. Use
`PUBLISH=true` com `REGISTRY` para publicar a imagem; use `DEPLOY=true` para
recriar somente SeaweedFS, o proxy OIDC e o reverse-proxy no Compose de
produção. O deploy exige que o container atual esteja saudável e possui
rollback automático para a imagem anterior. O volume persistente não é
removido.

O Compose aceita `SEAWEEDFS_IMAGE` para que a pipeline injete a imagem
construída sem alterar o `.env` protegido. O valor padrão continua sendo
`bookrush/seaweedfs:4.46`. O procedimento, parâmetros e limites estão em
[`docs/operations/jenkins-pipelines.md`](../../docs/operations/jenkins-pipelines.md).

Se o container existente estiver parado (`exited`), a pipeline dedicada pode
recuperá-lo preservando o volume persistente; essa exceção é limitada ao job
`bookrush-seaweedfs` e não permite substituir um container `unhealthy`.

### Importação Gutenberg (opt-in)

O estágio **Importação Gutenberg (opt-in)** fica ignorado no fluxo normal. Para
executá-lo, marque `RUN_GUTENBERG_IMPORT`, informe apenas IDs numéricos em
`GUTENBERG_EXTERNAL_IDS` e mantenha `GUTENBERG_DRY_RUN` marcado na primeira
execução. O job usa a credential Jenkins Secret text indicada por
`INGESTION_CREDENTIAL_ID` como segredo do client OIDC
`bookrush-ingestion-admin`, solicita um access token de curta duração a cada
execução e nunca grava o segredo ou o token no relatório. A chave de
idempotência inclui também o identificador da build Jenkins: uma mesma build
continua idempotente, mas uma nova tentativa deliberada pode reprocessar o
mesmo livro sem receber o resultado de uma tentativa anterior que falhou.

O estágio declara Bash como o primeiro byte do script do agente, pois usa
arrays, `[[ ]]` e here-strings para validar os IDs e o JSON.
O agente Jenkins também inclui `jq` na imagem base; ele é usado para validar a
resposta OIDC, montar o payload e acompanhar o job Gutenberg.

Na instalação Compose, essa Secret Text é provisionada idempotentemente pelo
`init.groovy.d/bookrush.groovy`, convertendo o valor para o tipo Secret do
Jenkins e usando o mesmo segredo protegido do client
OIDC do Jenkins. O provisionamento ocorre somente no startup do Jenkins e não
grava o segredo no repositório.

Para reprocessar um livro já importado e atualizar o EPUB, capítulos, TXT e
thumbnail, desmarque `GUTENBERG_DRY_RUN` e mantenha
`GUTENBERG_PROCESS_ASSETS` marcado. Esse modo envia `processAssets=true` e
`reprocess=true`; ele é deliberado e não é executado em deploys normais.

O estágio consulta o job até o limite escolhido em
`GUTENBERG_TIMEOUT_MINUTES`, arquiva `gutenberg-import-report.json` e falha a
pipeline para estados `FAILED`, `COMPLETED_WITH_ERRORS`, `CANCELLED` ou timeout.
Desmarcar `RUN_GUTENBERG_IMPORT` garante que pushes e deploys normais não
disparem importação.

O deploy usa o Compose do servidor montado em `/opt/bookrush/compose.yaml`
e o `.env` em `/run/bookrush.env`, ambos somente leitura. O profile `auth` é
ativado e o Keycloak é iniciado/aguardado junto com o proxy, catálogo,
ingestion-service e frontend. Atualiza as imagens dos serviços da aplicação,
faz um teste HTTP em `/api/status` e tenta restaurar as imagens anteriores se o
deploy falhar. O conjunto atualizado inclui catálogo, ingestão, analytics,
frontend, perfis de leitores, social, `admin-service` e `admin-web`; isso garante
que contratos usados pelas interfaces sejam implantados na mesma revisão.
PostgreSQL, Redis, pgAdmin e Jenkins são preservados.

Antes de aceitar a revisão, o deploy também obtém um token técnico do realm
administrativo e consulta `/api/v1/admin/users` pelo `admin-service`. Esse smoke
autenticado valida, em uma única chamada, os papéis do operador técnico, o
segredo do cliente de gerenciamento e o acesso ao realm de usuários; qualquer
falha aciona diagnóstico e rollback.

Os smokes aguardam por até um minuto a convergência de DNS, proxy e aplicações
depois dos healthchecks. A verificação de usuários atravessa o proxy de mesma
origem empacotado na própria imagem `admin-web` da revisão. Ela não depende do
`dynamic.yaml` montado no Traefik a partir do checkout estável do host, que pode
estar temporariamente atrás da branch. O console registra somente o status HTTP
de tentativas malsucedidas; bearer, secrets e respostas com usuários não são
expostos. Respostas `401` e `403` encerram as tentativas imediatamente porque
representam contrato de identidade, não atraso de inicialização.

Um job parametrizado pode carregar o Jenkinsfile da `main` antes de fazer o
checkout de uma feature. Enquanto a `main` ainda não conhecer as imagens
administrativas, `deploy-compose.sh` deriva suas tags das imagens de catálogo e
frontend e constrói localmente somente as que estiverem ausentes. Depois que o
Jenkinsfile novo estiver na `main`, as imagens são normalmente construídas e
publicadas no estágio **Imagens**, e esse fallback não executa builds adicionais.
O deploy combina o Compose estável de `/opt/bookrush/compose.yaml` com
`deploy-images.compose.yaml` da revisão selecionada, que aponta os serviços de
perfil e social para as imagens criadas pela construção atual.
Quando a thumbnail é protegida, a imagem do catálogo precisa ser atualizada
junto com o frontend. O Compose configura `CATALOG_PRODUCT_OIDC_ISSUER`,
`CATALOG_PRODUCT_OIDC_JWK_SET_URI` e `CATALOG_PRODUCT_OIDC_AUDIENCE` para que
o `catalog-service` valide o bearer do realm de produto; a pipeline deve
publicar/deployar `catalog-service` e `frontend` da mesma revisão.
O `.env` deve definir `BOOKRUSH_REPO_ROOT` com o caminho absoluto do checkout
visível ao daemon Docker; ele é usado para montar o realm do Keycloak e o
arquivo dinâmico do Traefik quando o Compose é executado de dentro do Jenkins.

Para o acesso público do Keycloak, o `.env` do servidor deve conter
`KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_PUBLIC_URL` e o
`INGESTION_OIDC_ISSUER` HTTPS correspondente ao DNS configurado. Sem essas
variáveis, o serviço pode iniciar com configuração local ou falhar por falta de
credenciais; o domínio e o certificado continuam sendo responsabilidade do
Nginx/DNS conforme [a documentação do Keycloak](../ingestion/keycloak.md).
Não há migração automática de banco neste esqueleto.

Para Compose, **REGISTRY** pode ficar vazio. Para repositório privado, cadastre
uma credencial HTTPS de usuário/token e informe **GIT_CREDENTIAL_ID**.
Kubernetes exige registry e a credencial de kubeconfig descrita em
`../kubernetes/README.md`; o Secret do banco deve existir no cluster.

## Atualizar a definição do job

`Jenkinsfile` é a fonte da pipeline. `bookrush.Jenkinsfile` é sua cópia de
bootstrap na imagem e precisa permanecer idêntica. O script de inicialização
não sobrescreve jobs existentes. Para alterar a pipeline de um job existente,
cole o novo `Jenkinsfile` em **Configure → Pipeline script** (sandbox ativado).
Alternativamente, depois de publicar o arquivo, configure **Pipeline script
from SCM**, repositório `https://github.com/jvteodoro/BookRush.git`, branch
`*/main`, Script Path `Jenkinsfile`. Essa branch fornece a definição da pipeline;
**BRANCH** seleciona o código que será compilado e implantado.

## Proxy e visualização de estágios

A imagem inclui `pipeline-stage-view` (tabela de estágios no job) e
`pipeline-graph-view` (grafo de cada execução). Após atualizar a imagem,
recrie somente o Jenkins quando não houver builds em andamento; o volume
`jenkins_home` preserva jobs, usuários e histórico.

Se aparecer o aviso de proxy reverso, confirme que o certificado foi emitido
**também para o domínio Jenkins**:

```bash
sudo certbot --nginx --redirect -d jenkins-bookrush.jteodoro.tec.br
```

A URL em **Manage Jenkins → System → Jenkins Location** deve ser
`https://jenkins-bookrush.jteodoro.tec.br/`. No Cloudflare, use **Full (strict)**.
Com HTTPS no Nginx, `$scheme` e `$server_port` encaminham `https` e `443`
ao Jenkins. Apenas ter HTTPS entre o navegador e o Cloudflare não basta.

## Integração de storage

A pipeline executa scripts/test-storage.sh antes de publicar/deploy: banco e
SeaweedFS reais em projeto temporário, sem volumes/portas fixas da aplicação.
O script usa docker cp em vez de bind do workspace porque o daemon está no host.
Presigned GET externo usa container com rede do host Linux. Os dois Jenkinsfiles
devem permanecer iguais.

Antes do primeiro deploy da API com storage, configurar STORAGE_* e
ASSET_ADMIN_TOKEN no .env do host e provisionar SeaweedFS conforme
docs/storage/operations.md. A pipeline executa infrastructure/jenkins/deploy-compose.sh da branch selecionada,
sem depender da cópia antiga em /opt/bookrush. Ela recusa deploy se storage não estiver
saudável. As alterações precisam estar publicadas na branch remota selecionada.
O deploy Compose exige as imagens `BACKEND_IMAGE`, `INGESTION_IMAGE`,
`ANALYTICS_IMAGE`, `READER_PROFILE_IMAGE` e `FRONTEND_IMAGE`; o Jenkinsfile
constrói/publica todas elas antes de executar o script de deploy. Essa matriz
também é refletida em `infrastructure/compose.yaml`, evitando que uma imagem
obrigatória fique apenas com o fallback `:local`.

Os jobs de social, publisher, behavior e admin devem executar `mvn verify` e
construir as imagens correspondentes depois de uma alteração. O admin-web usa
`VITE_USE_MOCKS=false`; o build falha deliberadamente se o modo mock for
ativado em produção. O `admin-service` só habilita o ciclo de vida Keycloak
quando `KEYCLOAK_ADMIN_CLIENT_SECRET` está presente no ambiente protegido e o
cliente possui `query-users`/`view-users`/`manage-users` no `realm-management` do realm de
produto `bookrush`. A autenticação humana do painel continua no realm separado
`bookrush-platform`.

Na primeira ativação do `admin-service`, provisionar e validar o cliente sem
exibir ou versionar o segredo:

```bash
infrastructure/keycloak/provision-admin-service-client.sh --env-file .env
infrastructure/keycloak/reconcile.sh --env-file .env
bash scripts/test-keycloak-admin-service-client.sh .env
```

O provisionamento é idempotente, mantém o segredo somente no `.env` protegido
e concede apenas `query-users`, `view-users` e `manage-users` à conta de serviço. O teste
valida token, roles e acesso à API de usuários sem imprimir credenciais.
Quando uma instalação anterior ainda não possui
`KEYCLOAK_ADMIN_CLIENT_SECRET` no arquivo protegido, a reconciliação cria o
cliente e o deploy consulta seu segredo pela API administrativa do Keycloak.
O valor é capturado com `xtrace` desativado, permanece apenas no processo de
deploy e é injetado no container do `admin-service`; ele não é gravado no
workspace nem exibido no log do Jenkins.

Essa consulta também é feita quando já existe uma cópia do segredo no arquivo
protegido. O valor vigente no Keycloak persistente tem precedência, evitando
falhas após criação ou rotação da credencial; a cópia configurada permanece
como fallback se a API administrativa não puder ser consultada.

O Nginx do `admin-web` resolve `admin-service` pelo DNS interno do Docker no
momento de cada chamada. Assim, o painel pode iniciar enquanto o serviço está
sendo recriado sem encerrar por uma corrida de DNS durante o deploy Compose.

O usuário permanente `codex-jenkins-agent` também permanece nos grupos
`operators` e `platform-operators`; a associação é reconciliada no realm e é
necessária para que a sessão OIDC obtenha permissão de disparar jobs Jenkins.
O cliente `bookrush-jenkins` também publica a audience `bookrush-ingestion`,
mantendo os tokens emitidos para smoke tests compatíveis com os serviços
de plataforma. O mapper `jenkins-subject` e o client scope `profile` também
emitem a claim OIDC `sub`, necessária para associar o actor autenticado às
ações administrativas; após alterar o realm, gere token novo e valide `iss`,
`aud`, `sub` e `groups` antes do smoke.
protegidos.

O dispatcher de behavior usa o sink persistente do `book-analytics-service`.
Provisionar no host com `infrastructure/operations/provision-behavior-outbox-sink.sh
.env`; o script mantém a URL e um token aleatório no `.env` protegido e nunca
imprime o token.

O deploy Compose preserva um Keycloak já ativo quando o container persistente
foi criado por uma instalação anterior e não possui os labels atuais do
Compose. Isso evita o conflito de nome `bookrush-keycloak-1` durante a
reconciliação ou rollback; o container continua na rede `bookrush_bookrush` e
os demais serviços são atualizados normalmente. Se o Keycloak não existir ou
estiver parado, o deploy volta a incluí-lo na operação.
