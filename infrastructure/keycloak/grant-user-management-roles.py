#!/usr/bin/env python3
"""Ensure the product user-management service account has minimal roles."""
from __future__ import annotations

import json
import os
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
print("Keycloak user management service account: roles reconciliadas")
