#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
mode=working
base_ref=""
while (($#)); do
  case "$1" in
    --staged) mode=staged ;;
    --ci) mode=ci ;;
    --push) mode=push ;;
    --base) shift; test "$#" -gt 0 || { echo '--base exige uma referência Git' >&2; exit 2; }; base_ref="$1" ;;
    --help) echo 'Uso: scripts/validate-change.sh [--staged|--push|--ci] [--base REF]'; exit 0 ;;
    *) echo "opção desconhecida: $1" >&2; exit 2 ;;
  esac
  shift
done

mapfile -t changed < <(
  case "$mode" in
    staged) git diff --cached --name-only --diff-filter=ACMR ;;
    push)
      if [[ -n "$base_ref" ]] && git rev-parse --verify "$base_ref" >/dev/null 2>&1; then
        git diff --name-only --diff-filter=ACMR "$base_ref...HEAD"
      elif git rev-parse --verify '@{upstream}' >/dev/null 2>&1; then
        git diff --name-only --diff-filter=ACMR '@{upstream}...HEAD'
      else
        git diff --name-only --diff-filter=ACMR HEAD^ HEAD
      fi
      ;;
    ci)
      if [[ -n "$base_ref" ]] && git rev-parse --verify "$base_ref" >/dev/null 2>&1; then
        git diff --name-only --diff-filter=ACMR "$base_ref...HEAD"
      elif git rev-parse --verify origin/main >/dev/null 2>&1 && [[ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]]; then
        git diff --name-only --diff-filter=ACMR origin/main...HEAD
      elif git rev-parse --verify HEAD^ >/dev/null 2>&1; then
        git diff --name-only --diff-filter=ACMR HEAD^ HEAD
      else
        git diff-tree --no-commit-id --name-only -r --root HEAD
      fi
      ;;
    working) git diff --name-only --diff-filter=ACMR ;;
  esac
)

if ((${#changed[@]} == 0)); then
  echo 'Change gate: nenhum arquivo alterado no intervalo analisado.'
  [[ "$mode" == ci ]] && bash scripts/validate-pipelines.sh
  exit 0
fi

has_path() { local pattern="$1" file; for file in "${changed[@]}"; do [[ "$file" == $pattern ]] && return 0; done; return 1; }
has_prefix() { local prefix="$1" file; for file in "${changed[@]}"; do [[ "$file" == "$prefix"* ]] && return 0; done; return 1; }
has_infrastructure_docs() {
  local file
  for file in "${changed[@]}"; do
    case "$file" in
      infrastructure/*.md|infrastructure/*/*.md|infrastructure/*/*/*.md) return 0 ;;
    esac
  done
  return 1
}

documentation_changed_for() {
  local prefix="$1" file
  for file in "${changed[@]}"; do
    case "$file" in
      "$prefix"docs/*|"$prefix"catalog-info.yaml|"$prefix"mkdocs.yml|"$prefix"api/*|docs/*|backstage/docs/*) return 0 ;;
    esac
  done
  return 1
}

errors=()
for service_dir in services/*; do
  [[ -d "$service_dir" ]] || continue
  service_name="${service_dir#services/}"
  if has_prefix "$service_dir/src/main/" || has_path "$service_dir/pom.xml" || has_path "$service_dir/Dockerfile"; then
    documentation_changed_for "$service_dir/" || errors+=("$service_dir mudou sem atualização de docs/catalog-info/mkdocs/api ou docs globais")
  fi
  if has_prefix "$service_dir/src/main/resources/db/migration/"; then
    has_prefix "$service_dir/docs/database/" || has_prefix docs/ || errors+=("migração de $service_name mudou sem documentação de banco")
  fi
done

if has_prefix frontend/src/ || has_path frontend/package.json || has_path frontend/Dockerfile; then
  documentation_changed_for frontend/ || errors+=("frontend mudou sem atualização de documentação")
fi
if has_prefix backstage/packages/ || has_prefix backstage/catalog/ || has_path backstage/app-config.yaml || has_path backstage/app-config.docker.yaml || has_path backstage/Dockerfile || has_path backstage/package.json; then
  has_prefix backstage/docs/ || has_prefix docs/ || errors+=("Backstage mudou sem atualização de documentação")
fi
if has_prefix infrastructure/ || has_path Jenkinsfile || has_path infrastructure/jenkins/bookrush.Jenkinsfile || has_path infrastructure/jenkins/backstage.Jenkinsfile || has_path compose.yaml; then
  has_prefix docs/ || has_infrastructure_docs || has_prefix backstage/docs/ || errors+=("infraestrutura/pipeline mudou sem atualização operacional")
fi
for service_dir in services/*; do
  [[ -d "$service_dir" ]] || continue
  if has_prefix "$service_dir/src/main/"; then
    has_prefix "$service_dir/api/" || has_prefix "$service_dir/docs/" || has_prefix docs/ || errors+=("código executável de $service_dir mudou sem contrato API ou documentação")
  fi
done

if ((${#errors[@]})); then
  printf 'Change gate: FALHOU\n' >&2
  printf ' - %s\n' "${errors[@]}" >&2
  echo 'Atualize a documentação/contrato correspondente no mesmo commit.' >&2
  exit 1
fi
if has_prefix services/book-analytics-service/config/ || has_prefix services/book-analytics-service/src/ || has_path scripts/validate-analytics-specs.py; then
  python3 scripts/validate-analytics-specs.py
fi
if [[ "$mode" == ci || "$mode" == push ]]; then bash scripts/validate-pipelines.sh; fi
echo "Change gate: OK (${#changed[@]} arquivos analisados)"
