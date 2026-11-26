#!/usr/bin/env python3
"""Patch only the product birthdate attribute and enable representation-free admin audit."""
import argparse
import json
import os
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", nargs="?", default="reconcile", choices=["reconcile", "fill", "correct"])
    args = parser.parse_args()
    base = os.environ["KEYCLOAK_AGE_ADMIN_URL"].rstrip("/")
    credentials = urlencode({
        "grant_type": "password",
        "client_id": "admin-cli",
        "username": os.environ.get("KEYCLOAK_CONFIG_CLI_ADMIN_USER", "admin"),
        "password": os.environ["KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD"],
    }).encode()
    try:
        with urlopen(Request(base + "/realms/master/protocol/openid-connect/token", data=credentials),
                     timeout=15) as response:
            token = json.load(response)["access_token"]

        def admin(path, payload=None, method=None):
            url = base + "/admin/realms/bookrush/" + path
            data = None if payload is None else json.dumps(payload).encode()
            headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
            verb = method or ("GET" if data is None else "PUT")
            with urlopen(Request(url, data, headers, method=verb), timeout=15) as response:
                return json.load(response) if verb == "GET" else None

        if args.mode == "reconcile":
            reconcile(admin)
        else:
            patch_users(admin, args.mode)
    except HTTPError as error:
        raise SystemExit(f"Birthdate reconcile rejected (HTTP {error.code}); verify provider and admin permissions") from None


def reconcile(admin):
    desired = json.loads((Path(__file__).parent / "age/birthdate-profile.json").read_text())
    profile_scope = next(scope for scope in admin("client-scopes") if scope["name"] == "profile")
    mappers = f"client-scopes/{profile_scope['id']}/protocol-mappers/models"
    for mapper in admin(mappers):
        if (mapper["protocolMapper"] == "oidc-usermodel-attribute-mapper"
                and mapper.get("config", {}).get("user.attribute") == "birthdate"):
            admin(f"{mappers}/{mapper['id']}", method="DELETE")
    profile = admin("users/profile")
    attributes = profile["attributes"]
    configured = next((a for a in attributes if a["name"] == "birthdate"), None)
    if configured != desired:
        if configured is None:
            attributes.append(desired)
        else:
            attributes[attributes.index(configured)] = desired
        admin("users/profile", profile)
    audit = admin("events/config")
    if not audit.get("adminEventsEnabled") or audit.get("adminEventsDetailsEnabled"):
        audit.update(adminEventsEnabled=True, adminEventsDetailsEnabled=False)
        admin("events/config", audit)
    print("User Profile birthdate and admin audit reconciled")


def patch_users(admin, mode):
    from urllib.parse import quote

    expected_issuer = os.environ["KEYCLOAK_AGE_PRODUCT_ISSUER"]
    profile = admin("users/profile")
    if not any("bookrush-birthdate" in a.get("validations", {}) for a in profile["attributes"]
               if a["name"] == "birthdate"):
        raise SystemExit("Reconcile the birthdate profile before updating users")
    audit = admin("events/config")
    if not audit.get("adminEventsEnabled") or audit.get("adminEventsDetailsEnabled"):
        raise SystemExit("Representation-free admin audit is required")
    updated = 0
    unchanged = 0
    for index, line in enumerate(sys.stdin, 1):
        if not line.strip():
            continue
        try:
            data = json.loads(line)
            if (data["issuer"] != expected_issuer or not isinstance(data["subject"], str)
                    or not data["subject"] or not isinstance(data["birthdate"], str) or not data["birthdate"]):
                raise ValueError
            endpoint = "users/" + quote(data["subject"], safe="")
            user = admin(endpoint)
            if user.get("id") != data["subject"]:
                raise ValueError
            current_attributes = user.get("attributes") or {}
            current_birthdate = current_attributes.get("birthdate", [])
            requested = [data["birthdate"]]
            if current_birthdate == requested:
                unchanged += 1
                continue
            if mode == "fill" and current_birthdate:
                raise ValueError
            if mode == "correct" and current_birthdate != [data["expectedBirthdate"]]:
                raise ValueError
            profile_fields = ("username", "email", "firstName", "lastName")
            payload = {key: user[key] for key in profile_fields if key in user}
            payload["attributes"] = {**current_attributes, "birthdate": requested}
            admin(endpoint, payload)
            updated += 1
        except (KeyError, TypeError, ValueError):
            raise SystemExit(f"Invalid row or stale birthdate at input line {index}") from None
    print(f"Birthdate batch finished: {updated} updated, {unchanged} unchanged")


if __name__ == "__main__":
    main()
