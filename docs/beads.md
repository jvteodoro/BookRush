# Beads no BookRush

Usamos o [Beads oficial](https://github.com/gastownhall/beads) **v1.2.2** para
rastrear tarefas dos agentes, com prefixo `bookrush`. O backend é Dolt
**embedded**: não precisa de servidor Dolt, Docker ou porta adicional.
O Beads não faz parte da aplicação nem da pipeline Jenkins.

## Instalação e uso

Instale o binário `bd` v1.2.2 da página de releases oficial correspondente ao
seu sistema. Confira o SHA-256 com o `checksums.txt` da mesma release antes de
instalar. Neste servidor, o binário está em `~/.local/bin/bd`.
Inclua esse diretório no PATH ou use o caminho completo:

```bash
export PATH="$HOME/.local/bin:$PATH"
bd version
bd prime
bd ready --json
```

Este checkout já foi inicializado. Em um novo clone, instale a mesma versão e
inicialize preservando as instruções de agentes versionadas:

```bash
bd init --prefix bookrush --non-interactive --skip-agents --skip-hooks
bd dolt pull
```

Não use flags de reinitialização forçada se houver dados locais. O `bd init`
pode criar um commit Git de bootstrap automaticamente. Não há hooks Git ou
hooks de IDE instalados por esta integração; a entrada do fluxo é `AGENTS.md`.
`CLAUDE.md` aponta para o mesmo fluxo. Isso também evita depender de escrita
nas pastas `.agents` e `.codex`, restritas neste ambiente.

## Ciclo de uma tarefa

```bash
bd ready --json
bd create "Implementar busca de livros" -t feature -p 2 --description "Escopo e critérios de aceitação" --json
bd show bookrush-XYZ --json
bd update bookrush-XYZ --claim --json
bd update bookrush-XYZ --append-notes "Implementação e evidências de validação"
bd close bookrush-XYZ --reason "Entregue e validado"
```

Substitua o ID de exemplo pelo retornado na criação. Para uma dependência:
`bd dep add <dependente> <bloqueador>`. Use `bd list --status in_progress --json`
para retomar trabalho existente antes de criar tarefas duplicadas.

## Persistência e sincronização

O banco local fica em `.beads/embeddeddolt/` e é ignorado pelo Git. Configuração,
metadados e instruções são versionados. `dolt.auto-commit: on` grava as mudanças
em commits **Dolt**, sem criar commits do código a cada tarefa.

O init configurou o remoto Dolt `origin` com o transporte Git:
`git+ssh://git@github.com/jvteodoro/BookRush.git`. Confira com `bd dolt remote list`.
A sincronização de tarefas é separada do push das branches do código:

```bash
bd dolt pull
bd dolt push
```

Esses comandos exigem autenticação SSH no GitHub e autorização de publicação
na sessão. O remoto estar configurado não significa que foi sincronizado.
Não publique tarefas com segredos. JSONL exportado serve para intercâmbio;
não substitui o banco ou um backup Dolt completo.

Para diagnóstico, use `bd doctor` e `bd dolt show`. Para backup completo, consulte
`bd backup --help` e configure um destino apropriado antes de depender dele.
