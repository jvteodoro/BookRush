# Fluxo dos agentes — BookRush

Estas instruções valem para todo o repositório. As instruções explícitas do
usuário e as regras do ambiente têm precedência.

## Rastreamento com Beads

Use **Beads (`bd`)** como registro persistente de tarefas, dependências e
continuidade entre sessões. Configuração e recuperação: [docs/beads.md](docs/beads.md).
Se `bd` não estiver no PATH, use `~/.local/bin/bd`.

Ao iniciar uma sessão ou retomar após compactação:

1. Leia este arquivo e execute `bd prime` para obter o contexto do Beads.
2. Execute `bd ready --json` e `bd list --status in_progress --json`.
3. Para a tarefa solicitada, consulte `bd show <id> --json`. Se ainda não existir,
   crie com `bd create "Título" -t task -p 2 --description "Escopo e conclusão esperada" --json`.
4. Assuma a tarefa com `bd update <id> --claim --json` antes de editar código.
   Não assuma tarefas já atribuídas a outro agente.

Durante o trabalho:

- Atualize notas com decisões relevantes, arquivos alterados, evidências de
  validação e impedimentos: `bd update <id> --append-notes "..."`.
- Registre trabalho descoberto em um novo bead e associe dependências com
  `bd dep add <dependente> <bloqueador>`. Não amplie o escopo sem necessidade.
- Não crie listas TODO em Markdown como um segundo rastreador. Documentação
  técnica e planos explicativos podem continuar em Markdown.
- Perguntas simples e respostas sem trabalho de implementação não exigem bead.
- Nunca registre senhas, tokens, conteúdo de `.env`, chaves ou cookies nos beads.

Ao encerrar:

- Execute as verificações adequadas e registre o resultado no bead.
- Conclua somente o que foi entregue: `bd close <id> --reason "Resultado e validação"`.
- Se houver pendência, mantenha a tarefa aberta ou bloqueada, com a próxima ação.
- Confira `bd ready --json` e `git status --short` e informe pendências ao usuário.
- Commits/push de código e sincronização de beads respeitam a autorização da
  sessão. Quando autorizada, a sincronização de tarefas usa `bd dolt pull` e
  `bd dolt push`; um `git push` de código não sincroniza o banco automaticamente.
  Se a autenticação falhar, informe a pendência; não declare que houve sync.
- Não execute `bd init --force`, restauração, exclusão de banco ou reescrita
  de histórico para contornar um erro. Preserve os dados e investigue.

## Desenvolvimento e validação

- React/TypeScript: `frontend/`; Java/Spring Boot: `services/catalog-service/`.
- Execute o build do frontend e os testes Java quando esses componentes mudarem.
  Os Dockerfiles permitem validar sem instalar Node/Maven no host.
- Para scripts Bash, use `bash -n`. Para a pipeline, mantenha `Jenkinsfile` e
  `infrastructure/jenkins/bookrush.Jenkinsfile` idênticos.
- A pipeline busca a branch remota. Mudanças locais não entram no deploy Jenkins.
- Preserve alterações do usuário, `.env` e volumes persistentes. Não reinicie
  serviços nem faça deploy apenas para validar uma alteração visual.
- Consulte `infrastructure/jenkins/README.md` para build/deploy e `README.md`
  para infraestrutura e teste React → API → PostgreSQL.
