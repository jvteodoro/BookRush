#!/usr/bin/env python3
"""Ensure the ingestion service account keeps its operator realm role."""
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
user = os.environ.get("KEYCLOAK_USER", "admin")
password = os.environ["KEYCLOAK_PASSWORD"]
realm = "bookrush-platform"
token_body = urllib.parse.urlencode(
    {
        "username": user,
        "password": password,
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

status, raw = request(
    f"{base}/admin/realms/{realm}/clients?clientId=bookrush-ingestion-admin",
    token=token,
)
clients = json.loads(raw)
if status != 200 or not clients:
    raise SystemExit("cliente bookrush-ingestion-admin não encontrado")
client_id = clients[0]["id"]
status, raw = request(
    f"{base}/admin/realms/{realm}/clients/{client_id}/service-account-user",
    token=token,
)
if status != 200:
    raise SystemExit("service account de ingestão não encontrado")
service_user_id = json.loads(raw)["id"]
status, raw = request(
    f"{base}/admin/realms/{realm}/roles/platform-operator",
    token=token,
)
if status != 200:
    raise SystemExit("role platform-operator não encontrada")
role = json.loads(raw)
status, _ = request(
    f"{base}/admin/realms/{realm}/users/{service_user_id}/role-mappings/realm",
    method="POST",
    token=token,
    body=[role],
)
if status not in (200, 204):
    raise SystemExit(f"não foi possível aplicar role ao service account (HTTP {status})")
print("Keycloak ingestion service account: platform-operator reconciliada")
