# Keycloak como infraestrutura declarativa

Os arquivos `infrastructure/keycloak/bookrush-realm.json` e
`bookrush-platform-realm.json` são a fonte versionada de realms, clients,
grupos, roles, scopes e políticas. O arquivo `.env` protegido do servidor é a
fonte atual dos secrets de runtime.

## Reconciliação

Após iniciar o Keycloak e confirmar que ele está saudável:

```bash
bash infrastructure/keycloak/reconcile.sh --env-file .env
```

O script:

1. carrega o `.env` apenas no ambiente do processo;
2. copia os JSONs para um diretório temporário privado;
3. injeta os secrets de clients fornecidos no `.env`;
4. executa o `keycloak-config-cli` em um container separado;
5. remove os arquivos temporários ao terminar.

Nenhum JSON versionado contém secrets. A reconciliação adota um realm populado
e não remove usuários. Omitir um secret permite preservar o valor já existente;
para uma execução estrita, use:

```bash
KEYCLOAK_RECONCILE_REQUIRE_CLIENT_SECRETS=true \
  bash infrastructure/keycloak/reconcile.sh --env-file .env
```

O bootstrap `--import-realm` do container Keycloak é reservado para volumes
vazios. Atualizações de uma instalação existente devem usar o reconciliador.

O serviço de reconciliação executa com o UID/GID do usuário que chamou o
script. Assim, o diretório temporário permanece `0700` e os JSONs renderizados
`0600`, sem tornar os secrets legíveis por outros usuários do host. A imagem
fixada do reconciliador é `adorsys/keycloak-config-cli:6.3.0-18.0.2`.

Para validar a configuração sem tocar no ambiente persistente, execute:

```bash
bash scripts/test-keycloak-reconcile.sh
```

Esse teste cria um Keycloak 25.0, uma rede e arquivos temporários descartáveis,
usa apenas secrets fictícios e remove tudo ao terminar. O teste não substitui
uma prova de login humano ou de migração de um realm produtivo.

## Variáveis protegidas

O `.env` de produção deve conter, além da senha administrativa temporária do
reconciliador, quando aplicável:

```text
JENKINS_OIDC_CLIENT_SECRET
BACKSTAGE_OIDC_CLIENT_SECRET
INGESTION_ADMIN_CLIENT_SECRET
INGESTION_CANONICAL_CLIENT_SECRET
INGESTION_ASSET_CLIENT_SECRET
KEYCLOAK_CONFIG_CLI_ADMIN_USER
KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD
```

Para habilitar o JCasC OIDC do Jenkins, acrescente também
`JENKINS_OIDC_CLIENT_ID`, `JENKINS_OIDC_CLIENT_SECRET` e
`JENKINS_OIDC_WELL_KNOWN_URL`, e defina
`CASC_JENKINS_CONFIG=/opt/bookrush/casc/jenkins-oidc.yaml`. O segredo é
injetado apenas no ambiente do container; o arquivo JCasC versionado contém
somente a referência à variável. O JCasC fixa `openid` como scope solicitado
pelo Jenkins; scopes operacionais de ingestão permanecem
exclusivos dos clients técnicos.

Esses valores não devem aparecer no Git, em imagens, no `docker compose config`
expandido ou em relatórios Jenkins. A adoção de SOPS+age fica planejada para
uma fase posterior de distribuição entre hosts.
