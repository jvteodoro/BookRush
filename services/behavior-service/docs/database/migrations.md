# Migrations do behavior-service

As migrations Flyway deste serviço são append-only. Nunca reescreva uma versão
já aplicada; mudanças de contrato recebem uma nova `Vn`.

## V11 — atribuição de outcomes

`V11__versioned_outcome_attribution.sql` adiciona ao bounded context
`behavior.outcome_attribution`:

- `attribution_status`: `ATTRIBUTED`, `ORGANIC`, `OUTSIDE_WINDOW` ou
  `INVALID_REFERENCE`;
- `attribution_rule_version`: atualmente `attribution-v1`;
- `attribution_window_seconds`: atualmente `604800` (sete dias).

A reconstrução recalcula esses campos a partir do log append-only. A migration
é compatível com linhas existentes por meio de defaults e cria apenas um índice
para consultas por status e tempo. Eventos históricos não são apagados nem
alterados retroativamente no log bruto.
