# ADR-007 — RAW, proveniência e direitos

Snapshots RAW são privados e não recebem `book_id` artificial. O catálogo guarda
apenas locators, hashes, observações e decisões de proveniência; o conteúdo fica
no object storage.

Direitos são decisões operacionais por asset/edição e ação (`DOWNLOAD`,
`PROCESSING`, `DISTRIBUTION`), território e período de validade. `UNKNOWN`,
`REVIEW_REQUIRED`, `REJECTED` e `REVOKED` bloqueiam distribuição. A licença
legada e a presença em um catálogo não autorizam distribuição automaticamente.
Evidência, política, ator e histórico são obrigatórios para uma decisão
aprovada. Revogação impede novos links; URLs assinadas já emitidas podem
continuar válidas até seu TTL, que deve ser curto e documentado.
