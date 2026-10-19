#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
fail() { echo "pipeline validation: $*" >&2; exit 1; }

test -f Jenkinsfile || fail 'Jenkinsfile ausente'
test -f infrastructure/jenkins/bookrush.Jenkinsfile || fail 'cópia do Jenkinsfile ausente'
cmp -s Jenkinsfile infrastructure/jenkins/bookrush.Jenkinsfile || fail 'Jenkinsfiles principais divergem'
test -f infrastructure/jenkins/backstage.Jenkinsfile || fail 'pipeline dedicada do Backstage ausente'
test -f infrastructure/jenkins/microservice.Jenkinsfile || fail 'pipeline de microserviço ausente'
test -f infrastructure/jenkins/vertical.Jenkinsfile || fail 'pipeline de vertical ausente'
test -f infrastructure/jenkins/seaweedfs.Jenkinsfile || fail 'pipeline do SeaweedFS ausente'
test -f infrastructure/jenkins/admin-web.Jenkinsfile || fail 'pipeline do admin-web ausente'

for file in scripts/validate-change.sh scripts/validate-pipelines.sh scripts/test-portal.sh scripts/test-storage.sh scripts/test-keycloak-realms.sh scripts/test-keycloak-reconcile.sh scripts/test-keycloak-access-lifecycle.sh scripts/test-keycloak-user-management.sh scripts/test-microservice.sh scripts/test-reader-bff-e2e.sh scripts/test-vertical.sh scripts/vertical-services.sh scripts/deploy-compose-service.sh scripts/deploy-kubernetes-service.sh infrastructure/jenkins/deploy-compose.sh infrastructure/jenkins/deploy-seaweedfs.sh infrastructure/bootstrap/start-environment.sh infrastructure/bootstrap/start-jenkins.sh; do
  test -f "$file" || fail "script referenciado não existe: $file"
  bash -n "$file" || fail "sintaxe inválida: $file"
done

grep -Fq 'bash scripts/validate-change.sh --ci' Jenkinsfile || fail 'Jenkinsfile principal sem change gate'
grep -Fq 'bash scripts/test-portal.sh --validation-only' Jenkinsfile || fail 'pipeline principal exporta imagem Backstage durante validação'
grep -Fq 'bash scripts/validate-change.sh --ci' infrastructure/jenkins/backstage.Jenkinsfile || fail 'pipeline Backstage sem change gate'
grep -Fq 'bash scripts/test-portal.sh' infrastructure/jenkins/backstage.Jenkinsfile || fail 'pipeline Backstage sem validação do portal'
grep -Fq 'scripts/test-storage.sh' Jenkinsfile || fail 'pipeline principal sem validação de storage'
grep -Fq 'bash scripts/test-keycloak-reconcile.sh' Jenkinsfile || fail 'pipeline principal sem validação de reconcile Keycloak'
grep -Fq 'bash scripts/test-keycloak-access-lifecycle.sh' Jenkinsfile || fail 'pipeline principal sem validação de ciclo de acesso Keycloak'
grep -Fq 'bash scripts/test-keycloak-user-management.sh' Jenkinsfile || fail 'pipeline principal sem validação de gerenciamento de usuários Keycloak'
grep -Fq 'bash scripts/test-admin-user-management-e2e.sh' Jenkinsfile || fail 'pipeline principal sem E2E do gerenciamento de usuários'
grep -Fq 'resolver 127.0.0.11' admin-web/nginx.conf || fail 'admin-web não resolve dinamicamente o admin-service durante deploy'
grep -Fq 'proxy_pass $admin_service;' admin-web/nginx.conf || fail 'admin-web sem proxy dinâmico para a API administrativa'
grep -Fq 'ADMIN_SERVICE_IMAGE="${BACKEND_IMAGE/bookrush\/catalog-service/bookrush\/admin-service}"' infrastructure/jenkins/deploy-compose.sh || fail 'deploy Compose sem fallback transitório da imagem admin-service'
grep -Fq 'ADMIN_WEB_IMAGE="${FRONTEND_IMAGE/bookrush\/frontend/bookrush\/admin-web}"' infrastructure/jenkins/deploy-compose.sh || fail 'deploy Compose sem fallback transitório da imagem admin-web'
test -f infrastructure/keycloak/read-client-secret.py || fail 'resolvedor seguro do segredo admin-service ausente'
grep -Fq 'grant-user-management-roles.py' infrastructure/jenkins/deploy-compose.sh || fail 'deploy Compose não reconcilia roles do gerenciamento de usuários'
grep -Fq 'live_admin_client_secret="$(docker run' infrastructure/jenkins/deploy-compose.sh || fail 'deploy Compose não consulta o segredo reconciliado do admin-service'
grep -Fq 'KEYCLOAK_ADMIN_CLIENT_SECRET="${live_admin_client_secret:-$configured_admin_client_secret}"' infrastructure/jenkins/deploy-compose.sh || fail 'deploy Compose não prioriza o segredo vigente do Keycloak'
grep -Fq 'if ! probe_user_management; then' infrastructure/jenkins/deploy-compose.sh || fail 'deploy Compose sem smoke autenticado de gerenciamento de usuários'
grep -Fq "'http://admin-web/api/v1/admin/users?page=0&size=1'" infrastructure/jenkins/deploy-compose.sh || fail 'smoke de usuários não atravessa o proxy da revisão do admin-web'
grep -Fq 'if ! probe_catalog; then' infrastructure/jenkins/deploy-compose.sh || fail 'deploy Compose sem retry do smoke de catálogo'
grep -Fq 'scripts/test-microservice.sh' infrastructure/jenkins/microservice.Jenkinsfile || fail 'pipeline de microserviço sem executor'
grep -Fq 'scripts/test-vertical.sh' infrastructure/jenkins/vertical.Jenkinsfile || fail 'pipeline de vertical sem executor'
grep -Fq 'scripts/deploy-compose-service.sh' infrastructure/jenkins/microservice.Jenkinsfile || fail 'pipeline de microserviço sem deploy Compose'
grep -Fq 'scripts/deploy-kubernetes-service.sh' infrastructure/jenkins/microservice.Jenkinsfile || fail 'pipeline de microserviço sem deploy Kubernetes'
grep -Fq 'scripts/deploy-compose-service.sh' infrastructure/jenkins/vertical.Jenkinsfile || fail 'pipeline de vertical sem deploy Compose'
grep -Fq 'bash scripts/test-storage.sh' infrastructure/jenkins/seaweedfs.Jenkinsfile || fail 'pipeline SeaweedFS sem teste de storage'
grep -Fq 'deploy-seaweedfs.sh' infrastructure/jenkins/seaweedfs.Jenkinsfile || fail 'pipeline SeaweedFS sem deploy dedicado'
grep -Fq 'docker build --target build' infrastructure/jenkins/admin-web.Jenkinsfile || fail 'pipeline admin-web sem build validado'
grep -Fq 'docker push "$ADMIN_WEB_IMAGE"' infrastructure/jenkins/admin-web.Jenkinsfile || fail 'pipeline admin-web sem publicação da imagem'
grep -Fq 'PUBLISH exige REGISTRY' infrastructure/jenkins/admin-web.Jenkinsfile || fail 'pipeline admin-web sem guarda de publicação'
echo 'Pipeline contracts: OK'
