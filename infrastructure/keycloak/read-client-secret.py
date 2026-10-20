#!/usr/bin/env python3
"""Print a Keycloak client secret for capture by a protected deploy process."""
from __future__ import annotations

import json
import os
import urllib.parse
import urllib.request


base = os.environ["KEYCLOAK_URL"].rstrip("/")
realm = os.environ.get("KEYCLOAK_REALM", "bookrush")
client_name = os.environ.get("KEYCLOAK_CLIENT_ID", "bookrush-admin-service")
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

headers = {"Authorization": f"Bearer {token}"}
query = urllib.parse.urlencode({"clientId": client_name})
with urllib.request.urlopen(
    urllib.request.Request(
        f"{base}/admin/realms/{realm}/clients?{query}", headers=headers
    ),
    timeout=20,
) as response:
    clients = json.loads(response.read())
if not clients:
    raise SystemExit(f"cliente {client_name} não encontrado no realm {realm}")

client_id = urllib.parse.quote(clients[0]["id"], safe="")
with urllib.request.urlopen(
    urllib.request.Request(
        f"{base}/admin/realms/{realm}/clients/{client_id}/client-secret",
        headers=headers,
    ),
    timeout=20,
) as response:
    secret = json.loads(response.read()).get("value", "")
if not secret:
    raise SystemExit(f"segredo do cliente {client_name} está vazio")
print(secret)
