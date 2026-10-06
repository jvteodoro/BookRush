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

O harness bibliográfico valida `docs/database/queries.sql` e exporta o caminho
absoluto em `scripts/test-database.sh` antes de iniciar o Compose. Isso evita
que o bind mount do fixture seja interpretado como diretório em agentes Jenkins
cujo diretório de execução não coincide com a raiz do repositório.

## Arquivos e operação

- `infrastructure/jenkins/microservice.Jenkinsfile`: pipeline reutilizável por serviço.
- `infrastructure/jenkins/vertical.Jenkinsfile`: pipeline reutilizável por vertical.
- `infrastructure/jenkins/init.groovy.d/bookrush.groovy`: cria/atualiza os 12 + 7 jobs.
- `infrastructure/jenkins/Dockerfile`: embarca os Jenkinsfiles no bootstrap.

As pipelines isoladas não fazem deploy. Publicação de imagem é opt-in somente
na pipeline de microserviço, com `PUBLISH=true`, `REGISTRY` e credential
configurados. O deploy permanece centralizado no `bookrush-deploy`.

Em uma instalação existente, é necessário reconstruir/recriar somente o
container Jenkins para que o `init.groovy.d` e os Jenkinsfiles novos sejam
carregados; o volume `jenkins_home` é preservado. Os jobs existentes não são
apagados nem têm histórico alterado.
