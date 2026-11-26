#!/usr/bin/env python3
"""Idempotently provision two synthetic identities in the isolated demo realm."""
import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, quote
from urllib.request import Request, urlopen

BASE = os.environ.get("KEYCLOAK_ADMIN_URL", "http://keycloak:8080").rstrip("/")
ADMIN = os.environ.get("KEYCLOAK_CONFIG_CLI_ADMIN_USER", "")
ADMIN_PASSWORD = os.environ.get("KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD", "")
USERS = (
    ("DEMO_ADULT_USERNAME", "DEMO_ADULT_PASSWORD", "1990-01-01"),
    ("DEMO_MINOR_USERNAME", "DEMO_MINOR_PASSWORD", "2012-01-01"),
)


def call(path, token, method="GET", body=None):
    raw = None if body is None else json.dumps(body).encode()
    req = Request(BASE + path, data=raw, method=method,
                  headers={"Authorization": "Bearer " + token,
                           "Content-Type": "application/json"})
    try:
        with urlopen(req, timeout=10) as response:
            return response.status, response.read()
    except HTTPError as error:
        return error.code, error.read()


def main():
    if not ADMIN or not ADMIN_PASSWORD:
        raise SystemExit("Demo Keycloak administrator credential ausente")
    form = urlencode({"grant_type": "password", "client_id": "admin-cli",
                      "username": ADMIN, "password": ADMIN_PASSWORD}).encode()
    req = Request(BASE + "/realms/master/protocol/openid-connect/token", data=form,
                  headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urlopen(req, timeout=10) as response:
            token = json.load(response).get("access_token")
        if not token:
            raise SystemExit("Demo Keycloak não emitiu token administrativo")
        for user_key, password_key, birthdate in USERS:
            username = os.environ.get(user_key, "")
            password = os.environ.get(password_key, "")
            if not username or not password or username == ADMIN:
                raise SystemExit("Conta sintética incompleta na credential da demo")
            query = urlencode({"username": username, "exact": "true"})
            status, raw = call("/admin/realms/bookrush/users?" + query, token)
            if status != 200:
                raise SystemExit("Lookup da conta sintética falhou")
            found = json.loads(raw)
            created = False
            if not found:
                email = username + "@synthetic.invalid"
                status, _ = call("/admin/realms/bookrush/users", token, "POST", {
                    "username": username, "email": email, "enabled": True,
                    "emailVerified": True, "attributes": {"birthdate": [birthdate]},
                    "requiredActions": [],
                })
                if status not in (201, 409):
                    raise SystemExit("Criação de identidade sintética falhou")
                created = status == 201
                status, raw = call("/admin/realms/bookrush/users?" + query, token)
                found = json.loads(raw) if status == 200 else []
            if len(found) != 1:
                raise SystemExit("Identidade sintética ambígua")
            subject = found[0]["id"]
            if not found[0].get("attributes", {}).get("birthdate"):
                status, _ = call("/admin/realms/bookrush/users/" + quote(subject, safe=""), token,
                                 "PUT", {**found[0], "attributes": {**found[0].get("attributes", {}),
                                                                       "birthdate": [birthdate]}})
                if status not in (200, 204):
                    raise SystemExit("Nascimento sintético não pôde ser corrigido")
            # Preserve credentials on retries: only new synthetic accounts get a password.
            if created:
                status, _ = call("/admin/realms/bookrush/users/" + quote(subject, safe="") + "/reset-password",
                                 token, "PUT", {"type": "password", "value": password,
                                                "temporary": False})
                if status not in (200, 204):
                    raise SystemExit("Senha da identidade sintética não pôde ser definida")
        print("Demo Keycloak: identidades sintéticas adulto/menor disponíveis.")
    except (HTTPError, URLError, TimeoutError, ValueError) as failure:
        print("Demo Keycloak: seed de identidades sintéticas falhou.", file=sys.stderr)
        raise SystemExit(1) from failure


if __name__ == "__main__":
    main()
