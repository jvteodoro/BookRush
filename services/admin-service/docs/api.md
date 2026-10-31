# API administrativa

`POST /api/v1/admin/audit` registra ação de administração sem armazenar tokens; `GET /api/v1/admin/audit` consulta os últimos eventos. O subject vem da identidade autenticada.

## Usuários

`GET /api/v1/admin/users?search=&page=0&size=25` pesquisa e pagina todas as
contas do realm de produto, inclusive aquelas sem perfil local. Somente tokens
do realm de plataforma com os papéis canônicos `platform-admin` ou
`platform-operator` — ou com os grupos legados `platform-admins` e `operators`
— podem chamar a rota.

`POST /api/v1/admin/users/{subjectKey}/disable` e `/enable` recebem
`{"reason":"..."}`. A alteração é confirmada no Keycloak antes da auditoria;
desabilitar também revoga as sessões do leitor. Motivos vazios, paginação fora
dos limites e tentativa de alterar a própria identidade são rejeitados.

## Moderação

`GET /api/v1/admin/moderation/comments` lista comentários pendentes ou já
decididos. `POST /api/v1/admin/moderation/comments` recebe `commentId` e
`visibility` (`VISIBLE`, `HIDDEN` ou `REMOVED`) e registra a decisão somente
para usuários com papel de operador.

## Moderação completa de comentários

O admin-service é a fachada administrativa de moderação existente. O proxy do
admin-web encaminha `/api/v1/admin/**` ao serviço; o gateway público mantém esse
mesmo contrato. Exige token novo do realm `bookrush-platform`, audience
`bookrush-ingestion` e role `platform-admin`/`platform-operator` (ou grupos legados
já aceitos). Tokens expirados são rejeitados. CORS mantém a lista explícita de
origens em `BOOKRUSH_CORS_ALLOWED_ORIGINS`.

- `GET /api/v1/admin/moderation/comments/page`: `search` até 200 caracteres,
  `visibility` vazio ou `VISIBLE`/`HIDDEN`/`REMOVED`, `page >= 0`, `size` entre
  1 e 100. Retorna `items`, `page`, `size`, `totalItems`, `last`. Busca literal
  em texto, autor, subject e IDs do livro/comentário; ordenação por criação e ID decrescentes.
- `POST /api/v1/admin/moderation/comments`: UUID `commentId`, `visibility` e
  `reasonCode` obrigatório com 3 a 80 caracteres após trim. Ocultar/remover
  retira o comentário da listagem social; restaurar volta a disponibilizá-lo.
  O conteúdo original é preservado. O estado anterior e novo, motivo, operador
  e data são auditados atomicamente; repetir o estado atual retorna `updated:
  false` sem duplicar histórico. Decisões concorrentes bloqueiam a mesma linha
  do comentário antes de ler o estado anterior.
- `GET /api/v1/admin/moderation/comments/{commentId}/history`: últimas 100
  decisões, mais recentes primeiro, com `previous_visibility`, `visibility`,
  `reason`, `actor_subject`, `created_at`.

Erros: 400 para filtros, paginação ou decisão inválidos; 401 para token ausente,
inválido ou expirado; 403 sem permissão de operador; 404 para comentário ausente.
A listagem antiga sem paginação permanece disponível para consumidores existentes.
Denúncias continuam com ciclo independente: mudar visibilidade não resolve nem
ignora denúncias automaticamente.

A implementação usa tabelas já existentes, sem modificar migrations aplicadas.
Validar com os testes Java e `scripts/test-admin-user-management-e2e.sh`, que
exercita PostgreSQL 16 e Keycloak descartáveis pelo proxy do admin-web, incluindo
busca, paginação, justificativa, histórico e efeito na API social de leitura.

O teste de integração também exige 403 para um token recém-emitido sem papel de
operador, 401 para token inválido/ausente e preflight da origem autorizada. Suas
imagens com tags aleatórias são removidas ao final, preservando o cache de build
compartilhado e todos os volumes persistentes do projeto.

Comentários com `author_deleted_at` são exibidos como `REMOVED`, com o instante
da remoção pelo autor. Uma tentativa de restaurar recebe 409, mantendo denúncias
e decisões anteriores. A marca é propriedade do social-service (migration V8),
que deve ser implantado antes da nova revisão administrativa em deploy parcial.
