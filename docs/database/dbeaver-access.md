# Acesso ao PostgreSQL com DBeaver

O PostgreSQL do BookRush não é publicado na internet. O Compose o associa
somente a `127.0.0.1:${BOOKRUSH_POSTGRES_PORT}` no servidor. A conexão remota
deve usar um túnel SSH autenticado; Nginx, Traefik e Cloudflare não fazem parte
desse caminho.

## Pré-requisitos

- acesso SSH autorizado ao host;
- `BOOKRUSH_POSTGRES_PORT`, `POSTGRES_DB`, `DBEAVER_READONLY_USER` e
  `DBEAVER_READONLY_PASSWORD` disponíveis no `.env` protegido do host;
- o ambiente iniciado com a versão do Compose que publica a porta de loopback.

## Túnel SSH

No computador que executa o DBeaver, crie o túnel. Troque o usuário e host:

```bash
ssh -N -L 15432:127.0.0.1:15432 usuario@host-do-bookrush
```

Se `BOOKRUSH_POSTGRES_PORT` usar outro valor, substitua o segundo `15432`.
O primeiro é uma porta local livre no computador cliente e pode ser diferente.
Mantenha a sessão SSH aberta enquanto usar o DBeaver.

Também é possível configurar o túnel diretamente no driver PostgreSQL do
DBeaver. O resultado deve continuar sendo uma conexão ao endereço local do
túnel, nunca ao IP público do host.

## Configuração no DBeaver

| Campo | Valor |
| --- | --- |
| Host | `127.0.0.1` |
| Porta | `15432` (ou a porta local do túnel) |
| Banco | valor de `POSTGRES_DB` |
| Usuário | valor de `DBEAVER_READONLY_USER` |
| Senha | valor de `DBEAVER_READONLY_PASSWORD` |
| SSL no driver | desabilitado, pois o túnel SSH cifra o transporte |

A conta dedicada tem `CONNECT`, `USAGE` nos schemas de dados e `SELECT` em
tabelas, sequences e futuras tabelas dos schemas `catalog`, `ingestion` e
`analytics`. Ela não cria objetos, não executa migrations, nem altera dados.

## Operação e rotação

Crie e altere a conta com o script abaixo, executado a partir da raiz do
checkout no host:

```bash
bash infrastructure/database/provision-dbeaver-readonly.sh
```

O script exige as duas variáveis `DBEAVER_READONLY_*` no `.env`, é idempotente
e não imprime a senha. Para rotacionar, altere somente
`DBEAVER_READONLY_PASSWORD` no `.env` e execute-o novamente. Revogue o acesso
executando `DROP ROLE` dentro do PostgreSQL com uma conta administrativa; em
seguida, remova as variáveis do `.env` quando não forem mais necessárias.

Não publique `5432` em `0.0.0.0`, não exponha essa porta por DNS e não use a
conta de leitura como credencial de serviços.
