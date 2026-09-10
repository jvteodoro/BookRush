# Validação

Na raiz use `bash scripts/test-portal.sh`. A imagem de validação usa Node 24,
Yarn lockfile e ferramentas Python isoladas. O script valida catálogo/relações/APIs,
renderização do template, links locais, builds MkDocs, TypeScript, lint, testes e bundle.
Os dois Jenkinsfiles contêm estágio dedicado antes de deploy. Nenhum teste depende
requisitar a API de produção ou importar livros.

Templates são renderizados com o mesmo delimitador Nunjucks usado pelo Scaffolder.
Publicação real de PR exige GitHub credentials; teste local não escreve remotamente.
API mode events/both define intenção de design; não inventa broker ou mensagem.
O usuário implementa o contrato real antes de registrar a API correspondente.

O teste de runtime deve iniciar uma stack descartável com PostgreSQL próprio,
confirmar health, entidades no catálogo e artefatos TechDocs. Ausência de credenciais
GitHub/Keycloak de portal limita somente validação de publicação/login externos.
Consulte validation-results.md para comandos e resultados efetivamente executados.
