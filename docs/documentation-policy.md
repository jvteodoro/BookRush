# Política de documentação

> If it is not discoverable through Backstage, it is not documented.

1. Git é a fonte canônica da documentação, contratos e entidades.
2. Backstage é a interface oficial de descoberta e consulta; não é uma wiki editável.
3. PRs que alterem arquitetura atualizam seus documentos e diagramas.
4. Alterações REST/eventos atualizam OpenAPI/AsyncAPI versionados no mesmo PR.
5. Decisões arquiteturais significativas exigem ADR com alternativas e consequências.
6. Novos serviços entram no catálogo com owner Group e System existentes.
7. Novos serviços incluem TechDocs e documentação de desenvolvimento e testes.
8. Serviços operacionalmente relevantes incluem runbook e observabilidade.
9. Documentos passam pelo mesmo review e checks de CI do código.

## Gates obrigatórios para agentes e desenvolvedores

O repositório fornece hooks versionados em `.githooks/`. Depois de clonar,
instale-os com:

```bash
bash scripts/install-hooks.sh
```

O hook `pre-commit` executa `scripts/validate-change.sh --staged` e bloqueia
alterações de código, infraestrutura, Backstage ou contratos sem documentação
correspondente no mesmo conjunto de mudanças. O hook `pre-push` também verifica
os contratos das pipelines Jenkins.

Esses hooks são uma primeira barreira para agentes e desenvolvedores. A barreira
autoritativa é a pipeline: os Jenkinsfiles executam o mesmo change gate antes de
builds, testes ou deploy. `scripts/validate-pipelines.sh` confirma que o
Jenkinsfile principal e sua cópia embarcada permanecem idênticos, que scripts
referenciados existem e que todos possuem sintaxe Bash válida.

Não use `--no-verify` para contornar o requisito. Se uma alteração realmente
for apenas código interno sem mudança observável, registre essa justificativa no
review e mantenha os checks de CI verdes.

## Definition of Done de integração

- [ ] catalog-info.yaml válido, owner Group e System resolvíveis.
- [ ] mkdocs.yml e TechDocs compilam; links locais são válidos.
- [ ] Arquitetura, domínio, desenvolvimento, testes e deploy explicados.
- [ ] OpenAPI/AsyncAPI válidos quando aplicáveis; ausência justificada em api/README.md.
- [ ] Recursos, dependências e APIs consumidas/providas registrados corretamente.
- [ ] ADRs necessários revisados; runbook e observabilidade publicados.
- [ ] Docker e comandos reproduzíveis; nenhum segredo no Git.

Checks verdes não garantem qualidade semântica: o reviewer deve comparar diagramas,
API exportada, migrations e código. Datas de validação e limitações devem ser honestas.
