#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$repo_dir/platform/bookrush-security-spring-boot-starter/src/test/resources/product-access-keycloak-test-realm.json"
container="$(docker run -d -p 127.0.0.1::8080 --mount "type=bind,src=$fixture,dst=/opt/keycloak/data/import/product-access-test-realm.json,readonly" \
  quay.io/keycloak/keycloak:25.0 start-dev --import-realm)"
trap 'docker rm -f "$container" >/dev/null' EXIT

issuer="http://$(docker port "$container" 8080/tcp)/realms/product-access-test"
for _ in $(seq 1 60); do
  if curl -fsS --max-time 2 "$issuer/.well-known/openid-configuration" >/dev/null 2>&1; then
    BOOKRUSH_TEST_PRODUCT_ACCESS_ISSUER="$issuer" mvn -B -f "$repo_dir/platform/pom.xml" \
      -pl bookrush-security-spring-boot-starter -am test
    exit
  fi
  sleep 1
done
echo "Keycloak descartável não disponibilizou discovery OIDC" >&2
exit 1
