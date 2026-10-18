# Operação, rollout e rollback da plataforma

Este documento fecha a ordem operacional da plataforma Java e da autenticação
BookRush. Ele complementa o checklist de desenvolvimento, o runbook do Jenkins
e o ciclo de acesso administrativo.

## Ordem de ativação

1. Confirmar backup consistente de PostgreSQL, Keycloak e SeaweedFS e manter a
   conta break-glass fora do Git, no cofre operacional.
2. Validar `docker compose config`, healthchecks e a reconciliação declarativa
   dos realms `bookrush` e `bookrush-platform`.
3. Construir e testar a plataforma Maven e os serviços consumidores.
4. Publicar as imagens da revisão e confirmar os digests antes do deploy.
5. Atualizar primeiro Keycloak/proxy, depois serviços, BFFs e interfaces.
6. Executar smoke pelo gateway público com token novo, origem real e preflight
   CORS; registrar somente commit, job, build e resultado.
7. Confirmar logout, expiração, audience, roles, isolamento entre realms e
   funcionamento do Backstage/Jenkins antes de liberar a revisão.

## Critérios de aceite

Uma revisão só é operacionalmente concluída quando todos os itens abaixo
forem comprovados no mesmo ciclo de deploy:

- `401` sem token ou com token inválido;
- `403` com token válido sem role/scope;
- status de negócio esperado com issuer, audience, scope, origem e container
  corretos;
- preflight `OPTIONS` aprovado somente para origens allowlisted;
- healthcheck verde e imagem efetiva correspondente ao commit;
- migrations aplicadas e validação repetida sem alteração de checksum;
- logout e expiração impedindo novas chamadas autenticadas;
- Jenkins e Backstage usando clientes próprios do realm de plataforma;
- APIs do produto usando somente o realm `bookrush`;
- buckets, volumes e credenciais de máquina preservados.

## Rollback

O rollback deve ser acionado se o healthcheck, smoke público, migration ou
autorização falhar após o deploy:

1. interromper novas execuções da pipeline;
2. guardar o número do build, commit, imagem/digest e logs sanitizados;
3. restaurar as imagens da revisão anterior pelo mecanismo da pipeline;
4. não apagar volumes, usuários, sessões, migrations ou buckets;
5. validar novamente `/api/status`, CORS, autenticação e as rotas críticas;
6. se o problema for identidade, revogar o grant comprometido e usar
   `infrastructure/keycloak/break-glass.sh` somente sob janela autorizada;
7. abrir incidente com a causa, impacto, TTL dos tokens já emitidos e plano de
   retorno à revisão nova.

Não se faz rollback editando uma migration aplicada nem recriando realm ou
volume persistente. Tokens já emitidos permanecem válidos até o TTL efetivo;
por isso o relatório deve registrar o horário do incidente e o prazo de
expiração observado.

## Evidências reproduzíveis

Antes de fechar uma revisão, executar no checkout publicado:

```bash
bash scripts/validate-pipelines.sh
bash scripts/validate-change.sh --ci
bash scripts/test-keycloak-realms.sh
bash scripts/test-keycloak-reconcile.sh
bash scripts/test-keycloak-access-lifecycle.sh
bash scripts/test-portal.sh
bash scripts/test-storage.sh
bash scripts/test-compose.sh
```

O Jenkins deve então executar a branch remota, aguardar `building=false` e
`result=SUCCESS`, e confirmar o container efetivo. Para prova autenticada do
produto, use a credential `bookrush-product-e2e` no realm `bookrush`; o usuário
`codex-jenkins-agent` do realm `bookrush-platform` só prova o acesso técnico ao
Jenkins e não substitui essa identidade.

Não registrar senha, token, cookie, crumb, authorization code, segredo de
cliente ou conteúdo de `.env` nas evidências.
