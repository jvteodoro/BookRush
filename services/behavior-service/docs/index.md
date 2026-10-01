# behavior-service

Append-only behavioral events and aggregates. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Migrations V2/V3 store idempotent append-only events and rebuildable daily book aggregates.

## Export

`python3 scripts/export-parquet.py --source events.jsonl --cutoff 2026-10-01T00:00:00Z --output behavior.parquet` gera o artefato e um manifesto com schema, cutoff, contagem e SHA-256. O upload S3 deve ser executado pelo job operacional com credenciais fornecidas em runtime.

V4 adiciona registry de schemas e rejects observáveis para eventos inválidos; rejeições não entram no log canônico.

The ingestion endpoint derives `identity_subject` from the authenticated
principal and ignores identity fields supplied in the event body. Batches are
limited to 100 events and payloads to 16 KiB; retries remain idempotent through
the unique `event_key` constraint. This prevents a browser from attributing an
event to another user.
