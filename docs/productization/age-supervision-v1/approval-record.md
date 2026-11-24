# Registro de aprovação da política etária e de supervisão v1

**Estado** Aguardando revisão. Nenhum aceite formal está registrado no
`bookrush-fyw9` ou na documentação da política. Este registro consolida
a proposta existente e a evidência necessária para decidir cada ponto.

**Base** [Política proposta](README.md), [vetores de fronteira](fixtures.tsv)
e [contrato de autorização v1](../product-access-v1/README.md). As referências
legais estão na política. Jurídico deve registrar o enquadramento aplicável
e a versão das fontes que embasam seu parecer.

## Decisões para revisão

Os responsáveis abaixo são funções de aprovação. Cada linha pode receber
aceite, pedido de ajuste ou rejeição fundamentada por quem responde pela função.

| Decisão | Proposta atual | Aprovação | Risco sem decisão | Requisitos e testes afetados | Evidência de aprovação |
|---|---|---|---|---|---|
| P1 · Acesso de menores e gratuidade | Acesso geral exige vínculo familiar, benefício vigente e `PRODUCT_ACCESS`. Ferramentas exigidas, verificação e escopos de supervisão autorizados ficam disponíveis gratuitamente e sem assento Premium. Se o vínculo pago for inadequado, definir caminho gratuito antes de implementar o gate. | Produto define o fluxo. Jurídico aprova o enquadramento, inclusive recursos sociais e identidade restrita pré-vínculo. Privacidade revisa a exposição. | Condicionar proteção à assinatura, excluir menores indevidamente ou liberar acesso antes da autorização. | Sessão mínima, aceite, perda do benefício, controles de tempo, comunicação e recomendações. `minor_invited`, `minor_accepts`, `minor_active`, `expired_minor`, `expired_guardian`. | Decisão de Produto sobre vínculo pago ou gratuito, parecer sobre o fluxo e matriz de ferramentas gratuitas com acesso após expiração. |
| P2 · Autoria e delegação | Menores ficam impedidos de publicar na primeira versão. Adultos só publicam com direito comercial válido, delegação quando cabível e ownership. | Produto. Engenharia valida a matriz. | Ativar publicação por uma delegação comercial que deveria ser insuficiente ou negar direitos válidos de adulto. | `PUBLISH_WRITE`, delegação comercial e revogação. `minor_cannot_publish`, `adult_member`. | Matriz por idade, estado e papel aprovada, incluindo o que ocorre com delegações preexistentes ao atingir 18 anos. |
| J1 · Aferição etária por fluxo | Data civil obrigatória. Declaração e verificação são estados distintos. Uma política por ação determina quando a verificação é exigida, os sinais aceitos e o tratamento de prova indisponível ou insuficiente. | Jurídico determina as exigências aplicáveis. Produto aprova a jornada. Privacidade aprova coleta e evidência. | Tratar declaração como prova forte, coletar documentos em excesso ou bloquear usuários sem canal alternativo. | User Profile, classificação, cadastro, consulta de sessão, configuração e reconciliação Keycloak. `unknown`, `unknown_own_session`, `declaration_insufficient`. | Matriz versionada por ação, faixa e risco, com evidência mínima, validade, fallback, contestação e casos de prova válida, vencida e ausente. |
| J2 · Responsabilidade legal e contestação | Verificar responsável adulto, menor exato, vínculo e vigência por fluxo separado. Aceite comercial apenas cria membership. Redução e renúncia têm efeito imediato. Contestação com risco suspende os escopos afetados; ampliação exige nova revisão. | Jurídico aprova legitimidade, provas e resolução de conflitos. Privacidade aprova custódia. Produto nomeia responsáveis operacionais pela verificação e revisão. | Conceder acesso ao pagador ou terceiro errado, perpetuar concessão revogada ou deixar família sem recurso acessível. | Criação, revisão e revogação de grants, disputas, suspensão e separação de papéis. `payer_only`, `other_family`, `revoked`; incluir dois responsáveis em disputa e prova inconclusiva. | Procedimento aprovado com prova aceitável, autoridade de revisão, motivos, vigência, notificação, recurso, tratamento de conflito e exemplos de aprovação e recusa. |
| D1 · Escopos, dados e retenção | `PRODUCT_ACCESS`, `BOOKS_ACCESSED`, `READING_ACTIVITY` e `COMMENTS` independentes. Projeções mínimas, sem exportação em massa. Janela a partir da concessão do escopo e sujeita à retenção. Fora dela, descartar ou anonimizar conforme finalidade e base legal. | Privacidade aprova finalidade, necessidade, conteúdo e retenção. Jurídico revisa base e exceções. Produto confirma o mínimo funcional. | Expor histórico excessivo, comentários removidos ou dados adultos, ou manter cópias em projeções, logs e backups. | Visibilidade por escopo, retenção, exclusão, auditoria e fluxos de dados. `partial_books`, `partial_activity`, `partial_comments`, `old_jwt_majority`; incluir corte de janela e propagação de exclusão. | Inventário por campo e finalidade, prazo e responsável por classe de dado, tratamento de evidências e backups, critérios de exclusão e revisão de impacto conforme aplicável. |
| J3 · Corte civil aos 18 anos | Meia-noite em `America/Sao_Paulo`, com 1º de março quando o nascimento em 29/02 não tiver data correspondente. A supervisão termina inclusive para dados anteriores e com JWT ainda válido. A transição usa relógio de servidor e dados atuais. | Jurídico ratifica regra civil e jurisdição. Engenharia valida o cálculo. Privacidade valida o encerramento de acesso. | Encerrar a supervisão cedo demais ou prolongar a leitura parental depois da maioridade. | Idade derivada, maioridade, limites de cache e correções. `day_before_18`, `midnight_18`, `leap_feb28`, `leap_mar01`, `old_jwt_majority`. | Regra civil e fuso aprovados, instantes UTC esperados revisados e consequência de data corrigida antes e depois do corte registrada. |
| P3 · Legado, correção e suporte | Migração por coortes com aviso, atualização no IdP e revisão. Antes do corte, continuidade temporária sem inferir maioridade nem conceder novos direitos. No corte, data ausente deixa a conta na sessão restrita. Preservar sub, acesso a recursos de correção e proteções já efetivas no rollback. | Produto aprova critérios e comunicação por coorte. Privacidade e Jurídico aprovam dados e contestação. Engenharia aprova a execução e o rollback. | Bloqueio em massa, legado permanentemente presumido adulto ou rollback que reabra acessos revogados. | Backfill, correção, rollback, sessão mínima e suporte. `unknown`, `unknown_own_session`, `declaration_insufficient`; incluir correção, recusa, retry e rollback depois do corte. | Plano de coortes aprovado com critérios de avanço e pausa, responsável e procedimento de revisão, evidência de preservação do sub prevista no aceite de `bookrush-0yle`. |
| T1 · Gates e revogação | Manter o contrato v1. Fatos vêm do servidor, decisões sensíveis usam versão e vigência, e falta de fato obrigatório devolve `503`. Sem reaproveitar permissões vencidas. Sessão mínima e convites próprios seguem exceções de rota. | Engenharia, com revisão de Segurança. Privacidade revisa respostas que possam revelar vínculo ou idade. | Acesso stale após revogação ou aniversário, decisões divergentes entre serviços ou bloqueio das rotas necessárias ao próprio menor. | Contratos, deny por padrão, 401/403/503, reavaliação em cada página e semântica de revogação. `decision_down`, `revoked`, `old_jwt_majority`, `minor_accepts`. | Mapeamento ação→fato→owner, critério de frescor, matriz de indisponibilidade e test cases de token antigo, correção, negação no acesso direto e ações mínimas. |

## Registro do parecer

Para cada ID, registrar no Bead ou em artefato vinculado o resultado,
a função e o nome de quem aprovou, a data, a versão da política e dos
vetores avaliados, a referência à evidência e as condições de aceite.

Um parecer condicionado deve indicar quais condições precisam ser atendidas
antes de fechar o gate. Uma mudança aprovada deve aparecer na política
versionada e nos vetores aplicáveis. Testes já executados documentam
coerência técnica e não substituem as aprovações.

Com os pareceres registrados, conferir a correspondência entre a política
e os vetores, anotar as evidências no `bookrush-fyw9` e verificar
`bd ready --json` após o fechamento. O `bookrush-0yle` vem em seguida.
O `bookrush-5ine` continua dependendo da conclusão de `bookrush-0yle`.
