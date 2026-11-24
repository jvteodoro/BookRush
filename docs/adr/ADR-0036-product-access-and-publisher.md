# ADR 0036 — Autorização de produto e realms do publisher

Status: Proposto

Aplicar uma política compartilhada nos pontos de execução dos serviços, com
resolução de fatos e decisões dinâmicas no family-service e entitlement consultado
no subscription-service. O starter oferece um AuthorizationManager e um cliente
de decisão reutilizáveis. Cadeias próprias instalam a política explicitamente.
A rota fixa o emissor, JWKS e audience aceitos. Identidades humanas e técnicas
têm ações separadas. O BFF oferece bootstrap de sessão e compõe respostas.

No publisher, rotas autorais passam ao realm de produto e usam issuer e subject
persistidos. Revisão editorial usa rotas e tokens de plataforma. Ingestão ganha
scope e audience exclusivos. A migração é aditiva, com vinculação administrativa
auditada de autores legados e ownership em todas as mutações.

Consideramos aplicar o gate apenas no gateway, calcular acesso no frontend,
usar claims como fonte de direitos e criar um serviço genérico de autorização.
As duas primeiras opções deixam outros caminhos de acesso abertos, claims podem
sobreviver à revogação e um novo serviço acrescentaria operação a uma decisão
que depende diretamente da família. A decisão introduz dependências síncronas
e um orçamento explícito de disponibilidade. Ver [contratos e rollout](../productization/premium-family.md).
