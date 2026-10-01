# behavior-service

Append-only behavioral events and aggregates. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Migrations V2/V3 store idempotent append-only events and rebuildable daily book aggregates.
