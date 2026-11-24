# ADR 0035 — Assinatura, família e supervisão

Status: Proposto

O ADR 0034 define ownership por contexto e identidade por issuer e subject. Premium
introduz um direito comercial revogável e o acesso de menores depende também
de uma autorização de supervisão.

Adotar `subscription-service` para oferta, cobrança e entitlement do titular,
e `family-service` para ocupação, convites, delegação comercial e concessões
de supervisão. O Keycloak de produto é a fonte de identidade e nascimento.
A concessão de supervisão tem titularidade, aprovação, escopos e revogação próprios.
Histórico e comentários permanecem com seus respectivos produtores. Cada serviço
usa migrations e credencial de banco próprias e integra por HTTP ou outbox.

Foram consideradas roles comerciais no Keycloak, banco compartilhado e um único
serviço de família e cobrança. Roles têm janela de desatualização do bearer,
o banco compartilhado acopla migrations e o serviço único dá ao fluxo de cobrança
acesso desnecessário ao contexto de supervisão. A decisão exige observar atraso
de eventos e compatibilidade de contratos. A ativação depende das decisões
de Produto e Privacidade em [Premium e Família](../productization/premium-family.md).
