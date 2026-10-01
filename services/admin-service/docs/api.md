# API administrativa

`POST /api/v1/admin/audit` registra ação de administração sem armazenar tokens; `GET /api/v1/admin/audit` consulta os últimos eventos. O subject vem da identidade autenticada.
