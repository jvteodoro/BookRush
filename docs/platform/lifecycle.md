# Lifecycle comum das aplicações Docker

O host precisa ter Docker Engine e Compose compatíveis; instalação do SO e
Kubernetes estão fora do escopo. O lifecycle deve funcionar antes do Jenkins
estar autenticado.

```text
pré-requisitos → segredos autorizados → base saudável → migrations do owner
→ Keycloak saudável → reconcile IAM → dependentes → smoke
```

Cada módulo deve declarar imagem/versionamento, redes/portas loopback, volumes,
configuração, segredos, dependências, health/readiness, reconcile, upgrade,
backup/restore e smoke. `depends_on` condiciona ordem inicial, mas não substitui
retry e recuperação em runtime.

## Modos

* **rebuild**: checkout, imagens e configuração mais segredos autorizados; cria
  ambiente vazio e não recupera usuários ou histórico;
* **restore**: dependências compatíveis, backup consistente de banco/storage e
  configuração/segredos necessários; preserva `(iss, sub)`, roles, clients e
  artefatos da fixture.

`docker compose restart` não aplica mudanças de configuração. Alterações de
realm, JCasC ou aplicação devem executar o comando de reconcile correspondente,
com timeout, lock por ambiente e falha explícita. Jobs init concluídos devem ser
reexecutados quando seu conteúdo/hash mudar.

## Segurança operacional

Não versionar `.env`, `compose config` expandido, JCasC interpolado, exports de
realm com credenciais ou logs de reconciliação contendo segredos. Interfaces S3,
PostgreSQL e SeaweedFS mantêm credenciais de máquina e buckets privados; uma
sessão humana não concede acesso irrestrito a volumes ou buckets.

Neste host, o padrão operacional atual é um `.env` protegido, com permissões
restritas e fornecido fora do Git. SOPS + age continua documentado como opção
futura para distribuir o ambiente entre hosts; nenhum comando do bootstrap
atual exige essas ferramentas. Quando essa migração ocorrer, chaves privadas e
recipients reais ficarão fora do checkout e a ausência da chave deverá falhar
antes de iniciar um ambiente parcialmente liberado.

## Contrato para novo container

Um novo serviço deve fornecer `compose` modular, imagem fixada, healthcheck,
dependências e timeout, configuração pública separada de segredo, comando
`reconcile`, smoke local e procedimento de backup/restore. O serviço deve
documentar se autentica por OIDC, SAML, credencial de máquina ou não possui
interface humana. Não criar um script paralelo ao lifecycle existente.
