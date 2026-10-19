#!/usr/bin/env python3
"""Ensure the product user-management service account has minimal roles."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request


def request(url: str, method: str = "GET", token: str | None = None, body: object = None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as response:
        return response.status, response.read()


base = os.environ["KEYCLOAK_URL"].rstrip("/")
realm = "bookrush"
token_body = urllib.parse.urlencode(
    {
        "username": os.environ.get("KEYCLOAK_USER", "admin"),
        "password": os.environ["KEYCLOAK_PASSWORD"],
        "grant_type": "password",
        "client_id": "admin-cli",
    }
).encode()
token_request = urllib.request.Request(
    f"{base}/realms/master/protocol/openid-connect/token",
    data=token_body,
    headers={"Content-Type": "application/x-www-form-urlencoded"},
    method="POST",
)
with urllib.request.urlopen(token_request, timeout=20) as response:
    token = json.loads(response.read())["access_token"]

_, raw = request(f"{base}/admin/realms/{realm}/clients?clientId=bookrush-admin-service", token=token)
clients = json.loads(raw)
if not clients:
    raise SystemExit("cliente bookrush-admin-service não encontrado no realm bookrush")
client_id = clients[0]["id"]
_, raw = request(f"{base}/admin/realms/{realm}/clients/{client_id}/service-account-user", token=token)
service_user_id = json.loads(raw)["id"]
_, raw = request(f"{base}/admin/realms/{realm}/clients?clientId=realm-management", token=token)
realm_management_id = json.loads(raw)[0]["id"]
roles = []
for name in ("query-users", "view-users", "manage-users"):
    _, raw = request(
        f"{base}/admin/realms/{realm}/clients/{realm_management_id}/roles/{name}", token=token
    )
    roles.append(json.loads(raw))
status, _ = request(
    f"{base}/admin/realms/{realm}/users/{service_user_id}/role-mappings/clients/{realm_management_id}",
    method="POST",
    token=token,
    body=roles,
)
if status not in (200, 204):
    raise SystemExit(f"não foi possível aplicar roles de usuários (HTTP {status})")
# A persistent client may have Full Scope Allowed disabled. In that case,
# service-account assignments alone are not emitted in its access token. Add
# the same minimal roles to the client's own scope mappings instead of opening
# the client to every assigned role.
status, _ = request(
    f"{base}/admin/realms/{realm}/clients/{client_id}/scope-mappings/clients/{realm_management_id}",
    method="POST",
    token=token,
    body=roles,
)
if status not in (200, 204):
    raise SystemExit(f"não foi possível aplicar scope mappings de usuários (HTTP {status})")

# Validate the exact client-credentials path used by admin-service before the
# Compose revision is started. Credentials and tokens remain process-local.
_, raw = request(f"{base}/admin/realms/{realm}/clients/{client_id}/client-secret", token=token)
client_secret = json.loads(raw).get("value", "")
if not client_secret:
    raise SystemExit("segredo do cliente bookrush-admin-service ausente")
service_token_body = urllib.parse.urlencode(
    {
        "grant_type": "client_credentials",
        "client_id": "bookrush-admin-service",
        "client_secret": client_secret,
    }
).encode()
service_token_request = urllib.request.Request(
    f"{base}/realms/{realm}/protocol/openid-connect/token",
    data=service_token_body,
    headers={"Content-Type": "application/x-www-form-urlencoded"},
    method="POST",
)
with urllib.request.urlopen(service_token_request, timeout=20) as response:
    service_token = json.loads(response.read())["access_token"]
try:
    request(f"{base}/admin/realms/{realm}/users?first=0&max=1", token=service_token)
except urllib.error.HTTPError as error:
    raise SystemExit(
        f"validação da API de usuários falhou após reconcile (HTTP {error.code})"
    ) from error
print("Keycloak user management: roles, scopes e API reconciliados")
