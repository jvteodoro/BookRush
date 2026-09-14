# BookRush Java Platform

Este componente reúne o BOM Maven e bibliotecas técnicas compartilhadas. O
handbook raiz descreve instalação, lifecycle, testes e versionamento; esta
página mantém a entrada local do componente para que ele seja descoberto pelo
TechDocs sem criar uma segunda fonte normativa.

Os módulos são `bookrush-common-core`, `bookrush-common-web`,
`bookrush-security-spring-boot-starter`,
`bookrush-observability-spring-boot-starter` e `bookrush-test-support`. O BOM
alinha versões, mas não adiciona dependências ao classpath. Cada aplicação
continua dona de suas rotas, migrations, jobs e dados.

O componente não possui banco, scheduler ou runtime executável; serviços
declaram somente os módulos necessários e continuam donos de seu domínio.
