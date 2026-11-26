#!/usr/bin/env python3
"""Apply demo-only restrictions to the isolated product realm."""
import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


BASE = os.environ.get("KEYCLOAK_ADMIN_URL", "http://keycloak:8080").rstrip("/")
USER = os.environ.get("KEYCLOAK_CONFIG_CLI_ADMIN_USER", "")
PASSWORD = os.environ.get("KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD", "")


def request(path, token, method="GET", payload=None):
    body = None if payload is None else json.dumps(payload).encode()
    req = Request(BASE + path, data=body, method=method, headers={
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json",
    })
    with urlopen(req, timeout=10) as response:
        return response.status, response.read()


def main():
    if not USER or not PASSWORD:
        raise SystemExit("Demo Keycloak administrator credential ausente")
    form = urlencode({"grant_type": "password", "client_id": "admin-cli",
                      "username": USER, "password": PASSWORD}).encode()
    req = Request(BASE + "/realms/master/protocol/openid-connect/token", data=form,
                  headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urlopen(req, timeout=10) as response:
            token = json.load(response).get("access_token")
        if not token:
            raise SystemExit("Demo Keycloak não emitiu token administrativo")
        status, raw = request("/admin/realms/bookrush", token)
        if status != 200:
            raise SystemExit("Realm de produto da demo indisponível")
        realm = json.loads(raw)
        realm["registrationAllowed"] = False
        request("/admin/realms/bookrush", token, "PUT", realm)
        matches = json.loads(request("/admin/realms/bookrush/clients?clientId=bookrush-web", token)[1])
        if len(matches) != 1:
            raise SystemExit("Cliente web do realm da demo não encontrado")
        client = matches[0]
        redirect = "http://localhost:19080/*"
        origin = "http://localhost:19080"
        redirects = set(client.get("redirectUris") or [])
        origins = set(client.get("webOrigins") or [])
        redirects.add(redirect)
        origins.add(origin)
        client["redirectUris"] = sorted(redirects)
        client["webOrigins"] = sorted(origins)
        request("/admin/realms/bookrush/clients/" + client["id"], token, "PUT", client)
        print("Demo Keycloak: cadastro público desativado e callback localhost reconciliado.")
    except (HTTPError, URLError, TimeoutError, ValueError) as failure:
        print("Demo Keycloak: reconciliação restrita falhou; deployment bloqueado.", file=sys.stderr)
        raise SystemExit(1) from failure


if __name__ == "__main__":
    main()
