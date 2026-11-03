#!/usr/bin/env python3
"""Ensure the configured human admin belongs to the platform-admins group."""
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
admin_user = os.environ.get("KEYCLOAK_USER", "admin")
password = os.environ["KEYCLOAK_PASSWORD"]
target_user = os.environ.get("ADMIN_WEB_ADMIN_USERNAME", admin_user).strip()
if not target_user:
    raise SystemExit("ADMIN_WEB_ADMIN_USERNAME ausente")

token_body = urllib.parse.urlencode(
    {
        "username": admin_user,
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

_, raw = request(
    f"{base}/admin/realms/bookrush-platform/clients?clientId=labsoft-admin-web",
    token=token,
)
clients = json.loads(raw)
if not clients:
    raise SystemExit("cliente labsoft-admin-web não encontrado")
client_id = clients[0]["id"]
_, raw = request(
    f"{base}/admin/realms/bookrush-platform/clients/{client_id}/protocol-mappers/models",
    token=token,
)
mappers = json.loads(raw)
subject_mapper = {
    "name": "admin-web-subject",
    "protocol": "openid-connect",
    "protocolMapper": "oidc-sub-mapper",
    "config": {
        "claim.name": "sub",
        "access.token.claim": "true",
        "id.token.claim": "true",
        "userinfo.token.claim": "true",
    },
}
existing_mapper = next((mapper for mapper in mappers if mapper.get("name") == "admin-web-subject"), None)
if not existing_mapper:
    status, _ = request(
        f"{base}/admin/realms/bookrush-platform/clients/{client_id}/protocol-mappers/models",
        method="POST",
        token=token,
        body=subject_mapper,
    )
    if status not in (200, 201, 204):
        raise SystemExit(f"não foi possível reconciliar subject do Admin Console (HTTP {status})")

query = urllib.parse.urlencode({"username": target_user, "exact": "true"})
_, raw = request(f"{base}/admin/realms/bookrush-platform/users?{query}", token=token)
users = [user for user in json.loads(raw) if user.get("username") == target_user]
if not users:
    print(f"Keycloak admin console: usuário {target_user} ainda não existe; acesso não alterado")
    raise SystemExit(0)

_, raw = request(
    f"{base}/admin/realms/bookrush-platform/groups?search=platform-admins&exact=true",
    token=token,
)
groups = [group for group in json.loads(raw) if group.get("name") == "platform-admins"]
if not groups:
    raise SystemExit("grupo platform-admins não encontrado")

status, _ = request(
    f"{base}/admin/realms/bookrush-platform/users/{users[0]['id']}/groups/{groups[0]['id']}",
    method="PUT",
    token=token,
)
if status not in (200, 204):
    raise SystemExit(f"não foi possível conceder acesso ao Admin Console (HTTP {status})")
print(f"Keycloak admin console: subject e acesso de {target_user} reconciliados")
