#!/usr/bin/env bash
set -Eeuo pipefail

service="${1:-}"
case "$service" in
  admin-service|behavior-service|book-content-service|book-ingestion-service|book-analytics-service|catalog-service|publisher-service|reader-bff-service|reader-profile-service|reader-state-service|recommendation-service|social-service) ;;
  *)
    echo "microserviço inválido: ${service:-<vazio>}" >&2
    echo 'válidos: admin-service behavior-service book-content-service book-ingestion-service book-analytics-service catalog-service publisher-service reader-bff-service reader-profile-service reader-state-service recommendation-service social-service' >&2
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
docker run --rm --entrypoint mvn "$image" -B verify
docker build -t "bookrush/${service}:${BUILD_NUMBER:-local}" -f "$service_dir/Dockerfile" "$([[ "$service" == book-analytics-service ]] && echo "$service_dir" || echo .)"
echo "Microserviço $service: build e testes Maven OK"
