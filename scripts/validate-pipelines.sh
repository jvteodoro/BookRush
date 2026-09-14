#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
fail() { echo "pipeline validation: $*" >&2; exit 1; }

test -f Jenkinsfile || fail 'Jenkinsfile ausente'
test -f infrastructure/jenkins/bookrush.Jenkinsfile || fail 'cópia do Jenkinsfile ausente'
cmp -s Jenkinsfile infrastructure/jenkins/bookrush.Jenkinsfile || fail 'Jenkinsfiles principais divergem'
test -f infrastructure/jenkins/backstage.Jenkinsfile || fail 'pipeline dedicada do Backstage ausente'

for file in scripts/validate-change.sh scripts/validate-pipelines.sh scripts/test-portal.sh scripts/test-storage.sh infrastructure/jenkins/deploy-compose.sh infrastructure/bootstrap/start-environment.sh infrastructure/bootstrap/start-jenkins.sh; do
  test -f "$file" || fail "script referenciado não existe: $file"
  bash -n "$file" || fail "sintaxe inválida: $file"
done

grep -Fq 'bash scripts/validate-change.sh --ci' Jenkinsfile || fail 'Jenkinsfile principal sem change gate'
grep -Fq 'bash scripts/validate-change.sh --ci' infrastructure/jenkins/backstage.Jenkinsfile || fail 'pipeline Backstage sem change gate'
grep -Fq 'bash scripts/test-portal.sh' infrastructure/jenkins/backstage.Jenkinsfile || fail 'pipeline Backstage sem validação do portal'
grep -Fq 'scripts/test-storage.sh' Jenkinsfile || fail 'pipeline principal sem validação de storage'
echo 'Pipeline contracts: OK'
