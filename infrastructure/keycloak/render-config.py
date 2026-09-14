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
}


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
        for client in data.get("clients", []):
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
