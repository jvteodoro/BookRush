# Pipelines por microserviço e por vertical

O job `bookrush-deploy` continua sendo a validação integrada e o caminho de
deploy. Para reduzir o tempo de feedback, o Jenkins também cria pipelines
isoladas no bootstrap da instância.

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

## Arquivos e operação

- `infrastructure/jenkins/microservice.Jenkinsfile`: pipeline reutilizável por serviço.
- `infrastructure/jenkins/vertical.Jenkinsfile`: pipeline reutilizável por vertical.
- `infrastructure/jenkins/init.groovy.d/bookrush.groovy`: cria/atualiza os 12 + 7 jobs.
- `infrastructure/jenkins/Dockerfile`: embarca os Jenkinsfiles no bootstrap.

O job `bookrush-admin-web` valida e empacota a console administrativa. Ele
aceita `BRANCH` e `DEPLOY`; quando `DEPLOY=true`, recria somente o serviço
`admin-web` no Compose. A definição usa blocos declarativos separados para
`when` e `steps`, portanto deve ser validada com `scripts/validate-pipelines.sh`
antes de ser publicada no Jenkins. Como o job é criado como Pipeline inline,
seu estágio de checkout usa explicitamente o repositório e a branch informada;
ele não depende de `checkout scm` de Multibranch. O build e o lint rodam em um
container `node:22-alpine`, pois o agente Jenkins não precisa ter Node instalado.

As pipelines isoladas não fazem deploy. Publicação de imagem é opt-in somente
na pipeline de microserviço, com `PUBLISH=true`, `REGISTRY` e credential
configurados. O deploy permanece centralizado no `bookrush-deploy`.

Em uma instalação existente, é necessário reconstruir/recriar somente o
container Jenkins para que o `init.groovy.d` e os Jenkinsfiles novos sejam
carregados; o volume `jenkins_home` é preservado. Os jobs existentes não são
apagados nem têm histórico alterado.
