# ADR-005 — Idempotência entre S3 e PostgreSQL

O upload segue `hash → storage_intent → PUT condicional → HEAD/GET → commit`
com fence. A intenção e a operation key permitem consultar um objeto depois de
perder a resposta do banco. Um objeto verificado não é apagado como compensação
imediata; reconciliação decide depois se ele é órfão, respeitando grace period e
lock de candidato à exclusão.

Bytes iguais para um asset lógico resultam em `NOOP`; bytes diferentes criam
versão física imutável. `book_asset.current_version_id` só muda depois da
verificação. ETag não é tratado como SHA-256 e não há deduplicação física global.
