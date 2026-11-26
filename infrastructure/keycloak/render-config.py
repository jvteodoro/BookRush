#!/usr/bin/env python3
"""Render secret-free Keycloak realm files with runtime-only client secrets."""
from __future__ import annotations

import json
import os
import pathlib
import sys

SECRETS = {
    "bookrush-jenkins": "JENKINS_OIDC_CLIENT_SECRET",
    "bookrush-backstage": "BACKSTAGE_OIDC_CLIENT_SECRET",
    "bookrush-ingestion-admin": "INGESTION_ADMIN_CLIENT_SECRET",
    "bookrush-canonical-service-s2s": "INGESTION_CANONICAL_CLIENT_SECRET",
    "bookrush-asset-service-s2s": "INGESTION_ASSET_CLIENT_SECRET",
    "bookrush-seaweedfs": "SEAWEEDFS_OIDC_CLIENT_SECRET",
    "bookrush-recommendation-s2s": "FEED_EXCERPTS_CLIENT_SECRET",
    "bookrush-recommendation-behavior-s2s": "BEHAVIOR_S2S_CLIENT_SECRET",
    "bookrush-publisher-behavior-s2s": "PUBLISHER_BEHAVIOR_S2S_CLIENT_SECRET",
    "bookrush-publisher-metrics-s2s": "PUBLISHER_METRICS_CLIENT_SECRET",
    "bookrush-admin-service": "KEYCLOAK_ADMIN_CLIENT_SECRET",
    "bookrush-jenkins-release-s2s": "JENKINS_RELEASE_REPORTING_CLIENT_SECRET",
    "bookrush-catalog-product-access-s2s": "CATALOG_PRODUCT_ACCESS_CLIENT_SECRET",
    "bookrush-book-content-product-access-s2s": "BOOK_CONTENT_PRODUCT_ACCESS_CLIENT_SECRET",
    "bookrush-social-product-access-s2s": "SOCIAL_PRODUCT_ACCESS_CLIENT_SECRET",
    "bookrush-reader-profile-product-access-s2s": "READER_PROFILE_PRODUCT_ACCESS_CLIENT_SECRET",
    "bookrush-behavior-product-access-s2s": "BEHAVIOR_PRODUCT_ACCESS_CLIENT_SECRET",
    "bookrush-behavior-operations-s2s": "BEHAVIOR_OPERATIONS_CLIENT_SECRET",
    "bookrush-reader-bff-session-s2s": "READER_BFF_SESSION_CLIENT_SECRET",
    "bookrush-reader-bff-supervision-s2s": "READER_BFF_SUPERVISION_CLIENT_SECRET",
    "bookrush-reader-state-s2s": "READER_STATE_PRODUCT_ACCESS_CLIENT_SECRET",
    "bookrush-family-age-s2s": "FAMILY_IDENTITY_CLIENT_SECRET",
    "bookrush-family-s2s": "SUBSCRIPTION_FAMILY_CLIENT_SECRET",
    "bookrush-billing-s2s": "SUBSCRIPTION_BILLING_CLIENT_SECRET",
    "bookrush-reader-bff-supervision-s2s": "READER_BFF_SUPERVISION_CLIENT_SECRET",
    "bookrush-grafana": "GRAFANA_OIDC_CLIENT_SECRET",
}


def add_reader_state_workload(source: pathlib.Path, realm: dict) -> None:
    if os.environ.get("KEYCLOAK_READER_STATE_CLIENT_ENABLED", "false").lower() != "true":
        return
    secret_env = SECRETS["bookrush-reader-state-s2s"]
    if not os.environ.get(secret_env):
        raise SystemExit(f"{secret_env} ausente para bookrush-reader-state-s2s")
    fragment = json.loads((source / "family" / "bookrush-platform.fragment.json").read_text(encoding="utf-8"))
    for key, field, name in (
        ("clientScopes", "name", "product-access.evaluate"),
        ("clients", "clientId", "bookrush-reader-state-s2s"),
    ):
        existing = realm.setdefault(key, [])
        if not any(entry.get(field) == name for entry in existing):
            existing.append(next(entry for entry in fragment[key] if entry[field] == name))


def add_content_workloads(source: pathlib.Path, realm: dict) -> None:
    if os.environ.get("KEYCLOAK_CONTENT_DISTRIBUTION_CLIENTS_ENABLED", "false").lower() != "true":
        return
    base = json.loads((source / "family" / "bookrush-platform.fragment.json").read_text(encoding="utf-8"))
    fragment = json.loads((source / "family" / "content-distribution.fragment.json").read_text(encoding="utf-8"))
    scopes = realm.setdefault("clientScopes", [])
    if not any(entry["name"] == "product-access.evaluate" for entry in scopes):
        scopes.append(next(entry for entry in base["clientScopes"] if entry["name"] == "product-access.evaluate"))
    clients = realm.setdefault("clients", [])
    for client in fragment["clients"]:
        client_id = client["clientId"]
        secret_env = SECRETS[client_id]
        if not os.environ.get(secret_env):
            raise SystemExit(f"{secret_env} ausente para {client_id}")
        if not any(existing["clientId"] == client_id for existing in clients):
            clients.append(client)


def add_community_workloads(source: pathlib.Path, realm: dict) -> None:
    if os.environ.get("KEYCLOAK_COMMUNITY_CLIENTS_ENABLED", "false").lower() != "true":
        return
    family = json.loads((source / "family" / "bookrush-platform.fragment.json").read_text(encoding="utf-8"))
    fragment = json.loads((source / "family" / "community.fragment.json").read_text(encoding="utf-8"))
    scopes = realm.setdefault("clientScopes", [])
    decision = next(scope for scope in family["clientScopes"] if scope["name"] == "product-access.evaluate")
    for scope in [decision, *fragment["clientScopes"]]:
        if not any(existing["name"] == scope["name"] for existing in scopes):
            scopes.append(scope)
    clients = realm.setdefault("clients", [])
    for client in fragment["clients"]:
        name = client["clientId"]
        secret = SECRETS[name]
        if not os.environ.get(secret):
            raise SystemExit(f"{secret} ausente para {name}")
        if not any(existing["clientId"] == name for existing in clients):
            clients.append(client)


def add_reading_supervision(source: pathlib.Path, realm: dict) -> None:
    if os.environ.get("KEYCLOAK_READING_SUPERVISION_ENABLED", "false").lower() != "true":
        return
    fragment = json.loads((source / "family" / "reading-supervision.fragment.json").read_text(encoding="utf-8"))
    config = fragment[realm["realm"]]
    scopes = realm.setdefault("clientScopes", [])
    for scope in config["clientScopes"]:
        if not any(existing["name"] == scope["name"] for existing in scopes):
            scopes.append(scope)
    if realm["realm"] == "bookrush":
        for client in realm["clients"]:
            if client["clientId"] in ("bookrush-web", "bookrush-mobile"):
                optional = client.setdefault("optionalClientScopes", [])
                if "supervision.read" not in optional:
                    optional.append("supervision.read")
    else:
        if not os.environ.get("READER_BFF_SUPERVISION_CLIENT_SECRET"):
            raise SystemExit("READER_BFF_SUPERVISION_CLIENT_SECRET ausente")
        if not any(client["clientId"] == config["client"]["clientId"] for client in realm["clients"]):
            realm["clients"].append(config["client"])


def add_session_bootstrap(source: pathlib.Path, realm: dict) -> None:
    if os.environ.get("KEYCLOAK_SESSION_BOOTSTRAP_ENABLED", "false").lower() != "true":
        return
    fragment = json.loads((source / "family" / "session.fragment.json").read_text(encoding="utf-8"))
    is_product = realm["realm"] == "bookrush"
    base = "bookrush" if is_product else "bookrush-platform"
    family = json.loads((source / "family" / f"{base}.fragment.json").read_text(encoding="utf-8"))
    name = "family.invites" if is_product else "product-access.evaluate"
    scopes = realm.setdefault("clientScopes", [])
    desired = next(scope for scope in family["clientScopes"] if scope["name"] == name)
    for scope in ([desired, fragment["scope"]] if is_product else [desired]):
        if not any(existing["name"] == scope["name"] for existing in scopes):
            scopes.append(scope)
    if is_product:
        for client in realm["clients"]:
            if client["clientId"] in ("bookrush-web", "bookrush-mobile"):
                optional = client.setdefault("optionalClientScopes", [])
                for scope in ("session.view", "family.invites"):
                    if scope not in optional and scope not in client.get("defaultClientScopes", []):
                        optional.append(scope)
    else:
        if not os.environ.get("READER_BFF_SESSION_CLIENT_SECRET"):
            raise SystemExit("READER_BFF_SESSION_CLIENT_SECRET ausente")
        if not any(client["clientId"] == fragment["client"]["clientId"] for client in realm["clients"]):
            realm["clients"].append(fragment["client"])


def add_age_facts_workload(source: pathlib.Path, realm: dict) -> None:
    if realm.get("realm") != "bookrush" or os.environ.get("KEYCLOAK_AGE_PROFILE_ENABLED", "false").lower() != "true":
        return
    fragment = json.loads((source / "family" / "identity-reader.fragment.json").read_text(encoding="utf-8"))
    clients = realm.setdefault("clients", [])
    for desired in fragment["clients"]:
        current = next((item for item in clients if item.get("clientId") == desired["clientId"]), None)
        if current is None:
            clients.append(desired)
        else:
            current.update(desired)
    users = realm.setdefault("users", [])
    for desired in fragment["users"]:
        current = next((item for item in users if item.get("username") == desired["username"]), None)
        if current is None:
            users.append(desired)
        else:
            current.update(desired)
    mappings = realm.setdefault("clientScopeMappings", {})
    roles = mappings.setdefault("realm-management", [])
    for desired in fragment["clientScopeMappings"]["realm-management"]:
        current = next((item for item in roles if item.get("client") == desired["client"]), None)
        if current is None:
            roles.append(desired)
        else:
            current["roles"] = list(dict.fromkeys([*current.get("roles", []), *desired["roles"]]))


def add_subscription_context(source: pathlib.Path, realm: dict) -> None:
    name = realm.get("realm")
    if name == "bookrush":
        fragment = json.loads((source / "subscription" / "bookrush.fragment.json").read_text(encoding="utf-8"))
        scopes = realm.setdefault("clientScopes", [])
        for desired in fragment.get("clientScopes", []):
            if not any(item.get("name") == desired["name"] for item in scopes):
                scopes.append(desired)
    elif name == "bookrush-platform":
        fragment = json.loads((source / "subscription" / "bookrush-platform.fragment.json").read_text(encoding="utf-8"))
        for client in fragment.get("clients", []):
            secret_name = SECRETS.get(client.get("clientId"))
            if secret_name and not os.environ.get(secret_name):
                raise SystemExit(f"{secret_name} ausente para {client['clientId']}")
        for key, field in (("clientScopes", "name"), ("clients", "clientId")):
            target = realm.setdefault(key, [])
            for desired in fragment.get(key, []):
                if not any(item.get(field) == desired[field] for item in target):
                    target.append(desired)


def add_product_user_scopes(source: pathlib.Path, realm: dict) -> None:
    if realm.get("realm") != "bookrush":
        return
    family = json.loads((source / "family" / "bookrush.fragment.json").read_text(encoding="utf-8"))
    subscription = json.loads((source / "subscription" / "bookrush.fragment.json").read_text(encoding="utf-8"))
    client_scopes = realm.setdefault("clientScopes", [])
    for desired in family.get("clientScopes", []):
        if not any(item.get("name") == desired["name"] for item in client_scopes):
            client_scopes.append(desired)
    scopes = {item["name"] for item in [*client_scopes, *subscription.get("clientScopes", [])]}
    for desired in subscription.get("clientScopes", []):
        if not any(item.get("name") == desired["name"] for item in client_scopes):
            client_scopes.append(desired)
    grants = {"family.manage", "family.invites", "supervision.request", "subscription.read"}
    if not grants <= scopes:
        raise SystemExit("Required product user scopes are not configured")
    for client in realm.get("clients", []):
        if client.get("clientId") in ("bookrush-web", "bookrush-mobile"):
            defaults = client.setdefault("defaultClientScopes", [])
            client["defaultClientScopes"] = list(dict.fromkeys([*defaults, "profile", "email", "bookrush.catalog.read", *sorted(grants)]))


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: render-config.py SOURCE_DIR OUTPUT_DIR", file=sys.stderr)
        return 2
    source, output = map(pathlib.Path, sys.argv[1:])
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise SystemExit(f"diretório de saída não está vazio: {output}")
    require = os.environ.get("KEYCLOAK_RECONCILE_REQUIRE_CLIENT_SECRETS", "false").lower() == "true"
    for source_file in sorted(source.glob("*.json")):
        data = json.loads(source_file.read_text(encoding="utf-8"))
        if data.get("realm") == "bookrush-platform":
            add_reader_state_workload(source, data)
            add_content_workloads(source, data)
            add_community_workloads(source, data)
        if data.get("realm") in ("bookrush", "bookrush-platform"):
            add_subscription_context(source, data)
            add_product_user_scopes(source, data)
            add_reading_supervision(source, data)
            add_session_bootstrap(source, data)
            add_age_facts_workload(source, data)
        for client in data.get("clients", []):
            if client.get("clientId") == "bookrush-grafana":
                redirect_uri = os.environ.get("GRAFANA_OIDC_REDIRECT_URI", "")
                web_origin = os.environ.get("GRAFANA_OIDC_WEB_ORIGIN", "")
                if redirect_uri:
                    client["redirectUris"] = [redirect_uri]
                if web_origin:
                    client["webOrigins"] = [web_origin]
            env_name = SECRETS.get(client.get("clientId"))
            if not env_name:
                continue
            secret = os.environ.get(env_name, "")
            if secret:
                client["secret"] = secret
            elif require:
                raise SystemExit(f"{env_name} ausente para {client['clientId']}")
        target = output / source_file.name
        target.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        target.chmod(0o600)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
