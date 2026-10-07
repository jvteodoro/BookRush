# Disparo de pipelines Jenkins pelo agente

Este runbook descreve o fluxo usado por um agente automatizado para executar
pipelines no Jenkins protegido por Keycloak. Ele evita os erros recorrentes de
autenticação, CSRF, jobs inline desatualizados e parâmetros ainda não
materializados.

## Pré-condições

1. Publique o commit na branch remota antes de disparar o job. O Jenkins não
   executa alterações que existem somente no checkout local.
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
| Validar/construir um serviço | `bookrush-microservice-<service>` | Não |
| Validar uma vertical | `bookrush-vertical-<vertical>` | Não |
| Atualizar o stack Compose | `bookrush-deploy` | Sim, com `DEPLOY=true` |
| Atualizar a console administrativa | `bookrush-admin-web` | Sim, com `DEPLOY=true` |

Exemplo para o serviço alterado:

```bash
curl -fsS -X POST \
  -b "$COOKIE_JAR" -H "$crumb_field: $crumb" \
  https://jenkins-bookrush.jteodoro.tec.br/job/bookrush-microservice-book-ingestion-service/buildWithParameters \
  --data-urlencode BRANCH=main
```

Para atualizar a aplicação no Compose:

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

## Jobs inline e sincronização

Os jobs criados por `infrastructure/jenkins/init.groovy.d/bookrush.groovy`
usam `CpsFlowDefinition`: são pipelines inline, não Multibranch. Portanto:

- fazer push do Jenkinsfile não altera a definição já salva no Jenkins;
- `checkout scm` não funciona nesses jobs;
- a pipeline deve fazer checkout explícito do repositório e da branch;
- após alterar um Jenkinsfile, sincronize a definição pelo bootstrap controlado
  ou configure o job como **Pipeline script from SCM**;
- sincronização não deve apagar o job, o histórico ou o volume `jenkins_home`.

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
| `Bad substitution` | sintaxe Bash executada por `/bin/sh` | usar expansão POSIX ou `bash -lc` explicitamente |
| build mostra Jenkinsfile antigo | definição inline não foi sincronizada | atualizar a definição do job e confirmar `config.xml`/console |
| `invalid_code` no Keycloak | callback OIDC reutilizado | iniciar uma autorização nova; não repetir o callback antigo |

## Evidência mínima para o relatório

Registre somente dados não sensíveis: commit, job, número do build, parâmetros
não secretos, resultado e URL do build. Não registre senha, client secret,
Bearer token, crumb, cookie, authorization code ou conteúdo de `.env`.

Uma atualização de produção só deve ser reportada como concluída quando o
build de validação, o build de deploy e o health check do Compose estiverem
verdes.
