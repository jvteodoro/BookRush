# Versionamento e consumidor externo

`examples/platform-consumer` não herda o parent BookRush. Ele importa somente
`bookrush-bom` e declara `bookrush-common-core` sem versão, demonstrando o
contrato para um projeto fora do reactor.

```bash
rm -rf /tmp/bookrush-m2
mvn -f platform/pom.xml -Dmaven.repo.local=/tmp/bookrush-m2 install -DskipTests
mvn -f examples/platform-consumer/pom.xml -Dmaven.repo.local=/tmp/bookrush-m2 verify
```

A release inicial é `0.1.0-SNAPSHOT` durante a integração. Releases estáveis
devem seguir compatibilidade semântica, changelog e adoção opt-in por serviço.
Deprecações permanecem documentadas por pelo menos uma release; não há
publicação automática nem credencial de registry no repositório.
