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

## Build e deploy pelo navegador

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

### Pipeline dedicada do Backstage

`backstage.Jenkinsfile` valida e empacota somente o Developer Portal. Cadastre
esse arquivo como um segundo job Jenkins multibranch ou Pipeline SCM para que a
documentação possa ser verificada sem reconstruir os microserviços. Por padrão,
ela faz checkout, executa `scripts/test-portal.sh` e cria uma imagem local;
`PUBLISH` e `DEPLOY` são opt-ins.

Para publicar, informe `REGISTRY`, habilite `PUBLISH` e configure a credential
`REGISTRY_CREDENTIAL_ID`. Para atualizar o Compose do portal, habilite `DEPLOY`
no host que possui `backstage/.env` e acesso ao Docker. O deploy usa a imagem
gerada no próprio build e aguarda PostgreSQL e Backstage ficarem saudáveis.

### Importação Gutenberg (opt-in)

O estágio **Importação Gutenberg (opt-in)** fica ignorado no fluxo normal. Para
executá-lo, marque `RUN_GUTENBERG_IMPORT`, informe apenas IDs numéricos em
`GUTENBERG_EXTERNAL_IDS` e mantenha `GUTENBERG_DRY_RUN` marcado na primeira
execução. O job usa a credential Jenkins Secret text indicada por
`INGESTION_CREDENTIAL_ID` como Bearer token; o token nunca é gravado no
relatório. A chave de idempotência é derivada do commit, IDs, modo e URL, então
repetir o build não cria uma segunda execução lógica.

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
deploy falhar. PostgreSQL, Redis, pgAdmin e Jenkins são preservados.

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
