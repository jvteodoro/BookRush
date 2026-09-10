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
