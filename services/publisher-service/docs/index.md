# publisher-service

Publisher submissions and authorized metrics. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Consulte [API](api.md) para submissões.

Uma submissão pode avançar para `SUBMITTED` usando `POST /api/v1/publisher/submissions/{id}/submit`.

Submission ownership is scoped to the authenticated principal. Anonymous
requests are rejected before any database write, and retrying a submit only
changes the caller's own submission.
