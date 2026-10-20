pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a compilar e implantar (ex.: main ou feature/catalogo).')
    choice(name: 'DEPLOY_TARGET', choices: ['compose', 'kubernetes'], description: 'Destino do deploy.')
    string(name: 'GIT_CREDENTIAL_ID', defaultValue: '', description: 'Credential Git opcional para repositório privado (HTTPS).')
    string(name: 'REGISTRY', defaultValue: '', description: 'Registry sem barra final. Vazio: não publica imagens.')
    string(name: 'REGISTRY_CREDENTIAL_ID', defaultValue: 'docker-registry', description: 'Credential Jenkins de usuário/senha do registry.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Atualizar a aplicação no destino escolhido após o build.')
    string(name: 'KUBECONFIG_CREDENTIAL_ID', defaultValue: 'bookrush-kubeconfig', description: 'Credential Jenkins do tipo Secret file.')
    booleanParam(name: 'RUN_GUTENBERG_IMPORT', defaultValue: false, description: 'Executar explicitamente a importação Gutenberg depois dos testes. Desmarcado: nenhuma importação.')
    booleanParam(name: 'GUTENBERG_DRY_RUN', defaultValue: true, description: 'Executar a importação Gutenberg em modo dry-run (recomendado).')
    booleanParam(name: 'GUTENBERG_PROCESS_ASSETS', defaultValue: true, description: 'Baixar/reprocessar EPUB, capítulos, TXT e thumbnail quando a importação estiver habilitada.')
    string(name: 'GUTENBERG_EXTERNAL_IDS', defaultValue: '1342', description: 'IDs Gutenberg separados por vírgula; somente números.')
    string(name: 'INGESTION_BASE_URL', defaultValue: 'https://bookrush.jteodoro.tec.br/ingestion', description: 'Base externa do gateway para o ingestion-service.')
    string(name: 'INGESTION_CREDENTIAL_ID', defaultValue: 'bookrush-ingestion-token', description: 'Credential Jenkins Secret text com token OIDC do operador.')
    booleanParam(name: 'RUN_INGESTION_S2S_E2E', defaultValue: false, description: 'Validar client_credentials e dry-run da ingestão pelo gateway após o deploy.')
    choice(name: 'GUTENBERG_TIMEOUT_MINUTES', choices: ['5', '15', '30', '60'], description: 'Tempo máximo de polling do job Gutenberg.')
  }

  options { timestamps(); disableConcurrentBuilds(); skipDefaultCheckout(); timeout(time: 30, unit: 'MINUTES'); buildDiscarder(logRotator(numToKeepStr: '20')) }

  stages {
    stage('Checkout da branch') {
      steps {
        script {
          env.SELECTED_BRANCH = params.BRANCH.trim()
          sh 'git check-ref-format --branch "$SELECTED_BRANCH"'
          def remote = [url: 'https://github.com/jvteodoro/BookRush.git']
          if (params.GIT_CREDENTIAL_ID?.trim()) { remote.credentialsId = params.GIT_CREDENTIAL_ID.trim() }
          sh '''
            set -euo pipefail
            uid="$(id -u)"
            gid="$(id -g)"
            docker run --rm --volumes-from "$HOSTNAME" -w "$PWD" \
              -e HOST_UID="$uid" -e HOST_GID="$gid" node:20-bookworm \
              bash -lc 'for path in mobile/node_modules mobile/dist; do if [ -e "$path" ]; then chown -R "$HOST_UID:$HOST_GID" "$path"; fi; done'
          '''
          deleteDir()
          def revision = checkout([$class: 'GitSCM',
            branches: [[name: "refs/remotes/origin/${env.SELECTED_BRANCH}"]],
            userRemoteConfigs: [remote]])
          env.GIT_COMMIT = revision.GIT_COMMIT
          currentBuild.description = "${env.SELECTED_BRANCH} @ ${env.GIT_COMMIT.take(7)}"
          if (params.DEPLOY && params.DEPLOY_TARGET == 'kubernetes' && !params.REGISTRY?.trim()) {
            error('Deploy Kubernetes exige REGISTRY.')
          }
        }
      }
    }

    stage('Developer Portal e documentação') {
      steps {
        sh 'docker run --rm --volumes-from "$HOSTNAME" -w "$PWD" python:3.12-slim bash -lc "apt-get update -qq && apt-get install -y -qq --no-install-recommends git >/dev/null && python -m pip install --quiet --disable-pip-version-check pyyaml jsonschema && git config --global --add safe.directory \"$PWD\" && bash scripts/validate-change.sh --ci"'
        sh 'bash scripts/test-portal.sh --validation-only'
      }
    }

    stage('Validação Mobile') {
      steps {
        sh '''
          set -euo pipefail
          docker run --rm --user "$(id -u):$(id -g)" --volumes-from "$HOSTNAME" -w "$PWD/mobile" node:20-bookworm bash -lc '
            npm ci --no-audit --no-fund
            EXPO_NO_TELEMETRY=1 EXPO_NO_DOCTOR=1 npm run typecheck
            EXPO_NO_TELEMETRY=1 EXPO_NO_DOCTOR=1 npm run export
          '
        '''
        archiveArtifacts artifacts: 'mobile/dist/**', allowEmptyArchive: false, fingerprint: true
      }
    }

    stage('Contratos de infraestrutura') {
      steps {
        sh '''
          set -euo pipefail
          bash -n infrastructure/bootstrap/*.sh infrastructure/keycloak/*.sh infrastructure/jenkins/*.sh scripts/*.sh
          docker compose --project-name bookrush-contracts --file infrastructure/compose.yaml --env-file .env.example config >/dev/null
          bash scripts/validate-pipelines.sh
          bash scripts/test-keycloak-realms.sh
          bash scripts/test-keycloak-reconcile.sh
          bash scripts/test-keycloak-access-lifecycle.sh
          bash scripts/test-keycloak-user-management.sh
          bash scripts/test-admin-user-management-e2e.sh
          bash scripts/test-keycloak-jwks-rotation.sh
        '''
      }
    }

    stage('Imagens') {
      steps {
        script {
          def registry = params.REGISTRY?.trim() ? params.REGISTRY.trim().replaceAll('/$', '') : 'local'
          env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
          env.BACKEND_IMAGE = "${registry}/bookrush/catalog-service:${env.IMAGE_TAG}"
          env.FRONTEND_IMAGE = "${registry}/bookrush/frontend:${env.IMAGE_TAG}"
          env.INGESTION_IMAGE = "${registry}/bookrush/book-ingestion-service:${env.IMAGE_TAG}"
          env.ANALYTICS_IMAGE = "${registry}/bookrush/book-analytics-service:${env.IMAGE_TAG}"
          env.ANALYTICS_RUNTIME_IMAGE = "${registry}/bookrush/book-analytics-runtime:${env.IMAGE_TAG}"
          env.READER_PROFILE_IMAGE = "${registry}/bookrush/reader-profile-service:${env.IMAGE_TAG}"
          env.SOCIAL_IMAGE = "${registry}/bookrush/social-service:${env.IMAGE_TAG}"
          env.ADMIN_SERVICE_IMAGE = "${registry}/bookrush/admin-service:${env.IMAGE_TAG}"
          env.ADMIN_WEB_IMAGE = "${registry}/bookrush/admin-web:${env.IMAGE_TAG}"
        }
        sh 'docker build --target build -t "bookrush/catalog-test:$BUILD_NUMBER" -f services/catalog-service/Dockerfile .'
        sh 'docker run --rm "bookrush/catalog-test:$BUILD_NUMBER" mvn -B verify'
        sh 'docker build -t "$BACKEND_IMAGE" -f services/catalog-service/Dockerfile .'
        sh 'docker build -t "$FRONTEND_IMAGE" frontend'
        sh 'docker build -t "$INGESTION_IMAGE" -f services/book-ingestion-service/Dockerfile .'
        sh 'docker build -t "$ANALYTICS_IMAGE" services/book-analytics-service'
        sh 'docker build -t "$ANALYTICS_RUNTIME_IMAGE" services/book-analytics-runtime'
        sh 'docker build -t "$READER_PROFILE_IMAGE" -f services/reader-profile-service/Dockerfile .'
        sh 'docker build -t "$SOCIAL_IMAGE" -f services/social-service/Dockerfile .'
        sh 'docker build --target build -t "bookrush/admin-service-test:$BUILD_NUMBER" -f services/admin-service/Dockerfile .'
        sh 'docker run --rm "bookrush/admin-service-test:$BUILD_NUMBER" mvn -B verify'
        sh 'docker build -t "$ADMIN_SERVICE_IMAGE" -f services/admin-service/Dockerfile .'
        sh 'docker build -t "$ADMIN_WEB_IMAGE" admin-web'
      }
    }

    stage('Integração PostgreSQL e S3') {
      steps {
        sh 'bash scripts/test-storage.sh'
      }
    }

    stage('Reconciliar Keycloak Compose') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'compose' } }
      steps {
        script {
          withCredentials([string(credentialsId: params.INGESTION_CREDENTIAL_ID, variable: 'INGESTION_ADMIN_CLIENT_SECRET')]) {
            sh '''
          set -euo pipefail
          # Jenkins launches shell steps with xtrace enabled. Disable it before
          # sourcing the protected runtime file so credentials never enter the
          # build log.
          set +x
          set -a
          . /run/bookrush.env
          set +a
          set -x
          bash scripts/reconcile-keycloak-agent.sh
            '''
          }
        }
      }
    }

    stage('Importação Gutenberg (opt-in)') {
      when { expression { return params.RUN_GUTENBERG_IMPORT } }
      steps {
        script {
          withCredentials([string(credentialsId: params.INGESTION_CREDENTIAL_ID, variable: 'INGESTION_CLIENT_SECRET')]) {
            try {
              sh '''#!/usr/bin/env bash
                set -euo pipefail
                set +x
                mkdir -p artifacts
                report=artifacts/gutenberg-import-report.json
                base="${INGESTION_BASE_URL%/}"
                token_url="https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush-platform/protocol/openid-connect/token"
                fetch_token() {
                  curl --fail-with-body --silent --show-error --connect-timeout 10 --max-time 30 \
                    -X POST "$token_url" \
                    --data-urlencode 'grant_type=client_credentials' \
                    --data-urlencode 'client_id=bookrush-ingestion-admin' \
                    --data-urlencode "client_secret=$INGESTION_CLIENT_SECRET" \
                    | jq -er '.access_token'
                }
                INGESTION_TOKEN=$(fetch_token)
                ids="${GUTENBERG_EXTERNAL_IDS//[[:space:]]/}"
                test -n "$ids"
                IFS=',' read -r -a id_array <<< "$ids"
                for id in "${id_array[@]}"; do
                  [[ "$id" =~ ^[1-9][0-9]*$ ]] || { echo "ID Gutenberg inválido" >&2; exit 2; }
                done
                ids_json=$(printf '%s\\n' "${id_array[@]}" | jq -R . | jq -s .)
                dry_run=${GUTENBERG_DRY_RUN,,}
                process_assets=${GUTENBERG_PROCESS_ASSETS,,}
                request=$(jq -cn --argjson ids "$ids_json" --argjson dry "$dry_run" --argjson assets "$process_assets" \
                  '{source:"GUTENBERG",externalIds:$ids,languages:[],maxItems:($ids|length),dryRun:$dry,processAssets:$assets,reprocess:$assets}')
                idem=$(printf '%s|%s|%s|%s|%s' "$GIT_COMMIT" "$BUILD_TAG" "$ids" "$dry_run" "$base" | sha256sum | cut -d' ' -f1)
                response=$(mktemp)
                status=$(curl --fail-with-body --silent --show-error --connect-timeout 10 --max-time 30 \
                  -o "$response" -w '%{http_code}' -X POST "$base/api/admin/v1/ingestion/run" \
                  -H "Authorization: Bearer $INGESTION_TOKEN" -H 'Content-Type: application/json' \
                  -H "Idempotency-Key: jenkins-gutenberg-$idem" --data "$request") || {
                    jq -n --arg status "$status" '{status:"SUBMISSION_FAILED",httpStatus:($status|tonumber? // 0)}' > "$report"
                    cat "$response" >&2
                    exit 1
                  }
                job_id=$(jq -r '.jobId // empty' "$response")
                test -n "$job_id"
                jq -n --arg jobId "$job_id" --arg httpStatus "$status" --arg dryRun "$dry_run" \
                  '{status:"SUBMITTED",jobId:$jobId,httpStatus:($httpStatus|tonumber),dryRun:($dryRun=="true")}' > "$report"
                deadline=$(( $(date +%s) + GUTENBERG_TIMEOUT_MINUTES * 60 ))
                while :; do
                  now=$(date +%s); (( now < deadline )) || { jq '.status="TIMEOUT"' "$report" > "$report.tmp" && mv "$report.tmp" "$report"; exit 1; }
                  job_response=$(mktemp)
                  job_status=$(curl --silent --show-error --connect-timeout 10 --max-time 30 \
                    -o "$job_response" -w '%{http_code}' \
                    -H "Authorization: Bearer $INGESTION_TOKEN" "$base/api/admin/v1/ingestion/jobs/$job_id")
                  if [[ "$job_status" == 401 ]]; then
                    INGESTION_TOKEN=$(fetch_token)
                    job_status=$(curl --silent --show-error --connect-timeout 10 --max-time 30 \
                      -o "$job_response" -w '%{http_code}' \
                      -H "Authorization: Bearer $INGESTION_TOKEN" "$base/api/admin/v1/ingestion/jobs/$job_id")
                  fi
                  [[ "$job_status" =~ ^2[0-9][0-9]$ ]] || { cat "$job_response" >&2; exit 1; }
                  job=$(cat "$job_response")
                  rm -f "$job_response"
                  state=$(printf '%s\n' "$job" | jq -r '.status // "UNKNOWN"')
                  jq --arg state "$state" --argjson job "$job" '.lastPoll=$state | .job=$job' "$report" > "$report.tmp" && mv "$report.tmp" "$report"
                  case "$state" in
                    COMPLETED|COMPLETED_WITH_ERRORS|FAILED|CANCELLED)
                      [[ "$state" == COMPLETED ]] || exit 1
                      exit 0 ;;
                  esac
                  sleep 5
                done
              '''
            } finally {
              archiveArtifacts artifacts: 'artifacts/gutenberg-import-report.json', allowEmptyArchive: true, fingerprint: true
            }
          }
        }
      }
    }

    stage('Publicar') {
      when { expression { return params.REGISTRY?.trim() } }
      steps {
        withCredentials([usernamePassword(credentialsId: params.REGISTRY_CREDENTIAL_ID, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh 'echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin'
          sh 'docker push "$BACKEND_IMAGE" && docker push "$FRONTEND_IMAGE" && docker push "$INGESTION_IMAGE" && docker push "$ANALYTICS_IMAGE" && docker push "$ANALYTICS_RUNTIME_IMAGE" && docker push "$READER_PROFILE_IMAGE" && docker push "$SOCIAL_IMAGE" && docker push "$ADMIN_SERVICE_IMAGE" && docker push "$ADMIN_WEB_IMAGE"'
        }
      }
    }

    stage('Deploy Compose') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'compose' } }
      steps {
        sh 'bash infrastructure/jenkins/deploy-compose.sh'
      }
    }

    stage('Smoke S2S da ingestão') {
      when { expression { return params.RUN_INGESTION_S2S_E2E && params.DEPLOY && params.DEPLOY_TARGET == 'compose' } }
      steps {
        withCredentials([string(credentialsId: params.INGESTION_CREDENTIAL_ID, variable: 'INGESTION_CLIENT_SECRET')]) {
          sh '''#!/usr/bin/env bash
            set -euo pipefail
            INGESTION_BASE_URL="$INGESTION_BASE_URL" \
              INGESTION_CLIENT_SECRET="$INGESTION_CLIENT_SECRET" \
              bash scripts/test-ingestion-s2s-gateway.sh
          '''
        }
      }
    }

    stage('Deploy Kubernetes') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'kubernetes' && params.REGISTRY?.trim() } }
      steps {
        withCredentials([file(credentialsId: params.KUBECONFIG_CREDENTIAL_ID, variable: 'KUBECONFIG')]) {
          sh '''
            kubectl apply -k infrastructure/kubernetes/base
            kubectl -n bookrush set image deployment/catalog-service catalog-service="$BACKEND_IMAGE"
            kubectl -n bookrush set image deployment/frontend frontend="$FRONTEND_IMAGE"
            kubectl -n bookrush rollout status deployment/catalog-service --timeout=180s
            kubectl -n bookrush rollout status deployment/frontend --timeout=120s
          '''
        }
      }
    }
  }
}
