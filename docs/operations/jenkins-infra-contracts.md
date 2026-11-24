# Contratos de infraestrutura no `bookrush-deploy`

O estágio **Contratos de infraestrutura** executa verificações de shell,
Compose, APIs pelo gateway e Keycloak. O parâmetro `RUN_INFRA_CONTRACTS` controla
esse estágio e fica `true` por padrão, mantendo as verificações nas execuções
normais.

Para investigar o estágio **Imagens**, desmarque `RUN_INFRA_CONTRACTS` no
formulário do build. O estágio de contratos será pulado e a pipeline seguirá
para as imagens se os estágios anteriores passarem. Mantenha `DEPLOY=false`
durante o diagnóstico. Uma falha anterior ainda interrompe os estágios
seguintes, e pular contratos não corrige falhas de compilação das imagens.

O job `bookrush-deploy` carrega o `Jenkinsfile` da branch escolhida em `BRANCH`.
O bootstrap em `infrastructure/jenkins/init.groovy.d/bookrush.groovy` publica o
parâmetro no formulário do job; o padrão é atualizado quando o bootstrap é
recarregado no Jenkins. O gate `validate-change.sh --ci` exige que mudanças
na pipeline sejam acompanhadas por documentação operacional no mesmo commit.
