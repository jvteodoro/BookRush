# Template Java Spring Boot

O template java-spring-service gera Java 21 / Spring Boot conforme baseline existente,
Maven, Dockerfile sem root, Actuator health, catalog-info.yaml, mkdocs.yml/base, docs e api.
Não cria dependência real de broker ou banco só porque uma opção foi escolhida: a opção
registra intenção/dependência arquitetural para revisão. Nenhuma credencial é gerada.

Campos: name, description, owner Group, system, lifecycle, apiType rest/events/both/none,
database, messaging e repoUrl. OwnerPicker e EntityPicker usam entidades existentes.
fetch:template gera arquivos e publish:github:pull-request propõe alteração em
services/nome num repositório existente. O portal não cria repositórios automaticamente.

Após merge, inclua Location no índice Git. Não chamar catalog:register antes do merge:
o arquivo ainda não existe na branch canônica. Em dry-run/editor verifique arquivos
antes de executar publish. Novos serviços não são considerados completos até substituir
Not applicable yet por design real nas funcionalidades implementadas.
