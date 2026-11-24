#!/usr/bin/env bash
set -Eeuo pipefail

service="${1:-}"
case "$service" in
  admin-service|behavior-service|book-content-service|book-ingestion-service|book-analytics-service|catalog-service|publisher-service|reader-bff-service|reader-profile-service|reader-state-service|recommendation-service|social-service|subscription-service|family-service) ;;
  *)
    echo "microserviço inválido: ${service:-<vazio>}" >&2
    echo 'válidos: admin-service behavior-service book-content-service book-ingestion-service book-analytics-service catalog-service publisher-service reader-bff-service reader-profile-service reader-state-service recommendation-service social-service subscription-service family-service' >&2
    exit 2
    ;;
esac

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
service_dir="services/$service"
image="bookrush/microservice-test:${service}-${BUILD_NUMBER:-local}"

test -f "$service_dir/pom.xml"
test -f "$service_dir/Dockerfile"
docker build --target build -t "$image" -f "$service_dir/Dockerfile" "$([[ "$service" == book-analytics-service ]] && echo "$service_dir" || echo .)"
if [[ "$service" == subscription-service || "$service" == family-service ]]; then
  context=${service%-service}
  test_database="${context}_test"
  test_env_prefix="${context^^}"
  test_db=$(docker run --rm -d -e POSTGRES_PASSWORD=bookrush-test-only \
    -e POSTGRES_DB="$test_database" postgres:16-alpine)
  trap 'docker stop "$test_db" >/dev/null' EXIT
  for attempt in {1..30}; do
    if docker exec "$test_db" pg_isready -h 127.0.0.1 -U postgres -d "$test_database"; then
      break
    fi
    sleep 1
  done
  docker run --rm --network "container:$test_db" --entrypoint mvn \
    -e "${test_env_prefix}_TEST_JDBC=jdbc:postgresql://127.0.0.1:5432/$test_database" \
    -e "${test_env_prefix}_TEST_PASSWORD=bookrush-test-only" "$image" -B verify
else
  docker run --rm --entrypoint mvn "$image" -B verify
fi
docker build -t "bookrush/${service}:${BUILD_NUMBER:-local}" -f "$service_dir/Dockerfile" "$([[ "$service" == book-analytics-service ]] && echo "$service_dir" || echo .)"
echo "Microserviço $service: build e testes Maven OK"
