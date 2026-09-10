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
    string(name: 'GUTENBERG_EXTERNAL_IDS', defaultValue: '1342', description: 'IDs Gutenberg separados por vírgula; somente números.')
    string(name: 'INGESTION_BASE_URL', defaultValue: 'https://bookrush.jteodoro.tec.br/ingestion', description: 'Base externa do gateway para o ingestion-service.')
    string(name: 'INGESTION_CREDENTIAL_ID', defaultValue: 'bookrush-ingestion-token', description: 'Credential Jenkins Secret text com token OIDC do operador.')
    choice(name: 'GUTENBERG_TIMEOUT_MINUTES', choices: ['5', '15', '30'], description: 'Tempo máximo de polling do job Gutenberg.')
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
        sh 'bash scripts/test-portal.sh'
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
        }
        sh 'docker build --target build -t "bookrush/catalog-test:$BUILD_NUMBER" services/catalog-service'
        sh 'docker run --rm "bookrush/catalog-test:$BUILD_NUMBER" mvn -B verify'
        sh 'docker build -t "$BACKEND_IMAGE" services/catalog-service'
        sh 'docker build -t "$FRONTEND_IMAGE" frontend'
        sh 'docker build -t "$INGESTION_IMAGE" services/book-ingestion-service'
      }
    }

    stage('Integração PostgreSQL e S3') {
      steps {
        sh 'bash scripts/test-storage.sh'
      }
    }

    stage('Importação Gutenberg (opt-in)') {
      when { expression { return params.RUN_GUTENBERG_IMPORT } }
      steps {
        script {
          withCredentials([string(credentialsId: params.INGESTION_CREDENTIAL_ID, variable: 'INGESTION_TOKEN')]) {
            try {
              sh '''
                set -euo pipefail
                set +x
                mkdir -p artifacts
                report=artifacts/gutenberg-import-report.json
                base="${INGESTION_BASE_URL%/}"
                ids="${GUTENBERG_EXTERNAL_IDS//[[:space:]]/}"
                test -n "$ids"
                IFS=',' read -r -a id_array <<< "$ids"
                for id in "${id_array[@]}"; do
                  [[ "$id" =~ ^[1-9][0-9]*$ ]] || { echo "ID Gutenberg inválido" >&2; exit 2; }
                done
                ids_json=$(printf '%s\\n' "${id_array[@]}" | jq -R . | jq -s .)
                dry_run=${GUTENBERG_DRY_RUN,,}
                request=$(jq -cn --argjson ids "$ids_json" --argjson dry "$dry_run" \
                  '{source:"GUTENBERG",externalIds:$ids,languages:[],maxItems:($ids|length),dryRun:$dry,processAssets:false}')
                idem=$(printf '%s|%s|%s|%s' "$GIT_COMMIT" "$ids" "$dry_run" "$base" | sha256sum | cut -d' ' -f1)
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
                  job=$(curl --fail-with-body --silent --show-error --connect-timeout 10 --max-time 30 \
                    -H "Authorization: Bearer $INGESTION_TOKEN" "$base/api/admin/v1/ingestion/jobs/$job_id")
                  state=$(jq -r '.status // "UNKNOWN"' <<< "$job")
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
          sh 'docker push "$BACKEND_IMAGE" && docker push "$FRONTEND_IMAGE" && docker push "$INGESTION_IMAGE"'
        }
      }
    }

    stage('Deploy Compose') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'compose' } }
      steps {
        sh 'bash infrastructure/jenkins/deploy-compose.sh'
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
