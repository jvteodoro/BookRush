# admin-service

Administrative facade and audit operations. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Consulte [API](api.md) para os endpoints de auditoria.

A moderação é registrada em `admin.moderation` com actor subject e decisão auditável.
