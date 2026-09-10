# Integrar serviços

## Serviço novo

1. Em Create selecione Java Spring Boot — BookRush.
2. Informe nome kebab-case, descrição, Group owner, System, lifecycle e tipo de API.
3. Escolha dependência de banco e, se aplicável, Resource de mensageria já registrado.
4. Escolha repositório GitHub existente. O template propõe PR em services/nome.
5. Revise código, permissões, contratos e explicações Not applicable yet.
6. Após merge adicione Location do catalog-info.yaml ao índice raiz e rode checks.
7. Confira catálogo, dependências e TechDocs no portal.

## Serviço existente

Crie catalog-info.yaml Component com type/lifecycle/owner/system e annotation
backstage.io/techdocs-ref: dir:. quando mkdocs.yml está ao lado do YAML.
Crie mkdocs.yml herdando a base, docs/index.md e documentação de arquitetura, domínio,
banco, desenvolvimento, testes, operação e ADRs. Não copie páginas transversais:
linke o handbook e explique só o que é específico. Declare APIs e Resources reais.

Adicione o arquivo ao Location raiz. Execute validação local/CI e faça PR.
No Backstage confira erros de processamento na aba de erros do catálogo e logs.
Para repositórios privados configure integração GitHub antes de cadastrar a URL.
