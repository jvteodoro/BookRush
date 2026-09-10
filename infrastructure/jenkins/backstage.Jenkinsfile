pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch do portal a validar e empacotar.')
    string(name: 'GIT_CREDENTIAL_ID', defaultValue: '', description: 'Credential Git opcional para repositório privado.')
    string(name: 'REGISTRY', defaultValue: '', description: 'Registry sem barra final. Vazio: mantém a imagem local.')
    string(name: 'REGISTRY_CREDENTIAL_ID', defaultValue: 'docker-registry', description: 'Credential Jenkins de usuário/senha do registry.')
    string(name: 'BACKSTAGE_ENV_FILE', defaultValue: '/run/bookrush.env', description: 'Arquivo de ambiente disponível no agente/host de deploy.')
    booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Publicar a imagem do portal no registry.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Atualizar o stack Backstage via Compose no host de deploy.')
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    skipDefaultCheckout()
    timeout(time: 30, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

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
        }
      }
    }

    stage('Validar catálogo e TechDocs') {
      steps { sh 'bash scripts/test-portal.sh' }
    }

    stage('Preparar imagem') {
      steps {
        script {
          def registry = params.REGISTRY?.trim() ? params.REGISTRY.trim().replaceAll('/$', '') : 'local'
          env.PORTAL_IMAGE = "${registry}/bookrush/backstage:${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
        }
        sh 'docker tag bookrush/portal:local "$PORTAL_IMAGE"'
      }
    }

    stage('Publicar imagem') {
      when { expression { return params.PUBLISH && params.REGISTRY?.trim() } }
      steps {
        withCredentials([usernamePassword(credentialsId: params.REGISTRY_CREDENTIAL_ID, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh 'echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin'
          sh 'docker push "$PORTAL_IMAGE"'
        }
      }
    }

    stage('Deploy Backstage Compose') {
      when { expression { return params.DEPLOY } }
      steps {
        sh '''
          set -euo pipefail
          env_file="${BACKSTAGE_ENV_FILE:-/run/bookrush.env}"
          test -f "$env_file" || { echo "arquivo de ambiente ausente: $env_file" >&2; exit 2; }
          deploy_env=$(mktemp)
          trap 'rm -f "$deploy_env"' EXIT
          cp "$env_file" "$deploy_env"
          if ! grep -Eq '^BACKSTAGE_POSTGRES_PASSWORD=.+$' "$deploy_env"; then
            postgres_line=$(grep -E '^POSTGRES_PASSWORD=.+$' "$deploy_env" || true)
            test -n "$postgres_line" || { echo 'BACKSTAGE_POSTGRES_PASSWORD ou POSTGRES_PASSWORD precisa estar definido' >&2; exit 2; }
            printf 'BACKSTAGE_POSTGRES_PASSWORD%s\n' "${postgres_line#POSTGRES_PASSWORD}" >> "$deploy_env"
          fi
          BACKSTAGE_IMAGE="$PORTAL_IMAGE" docker compose --env-file "$deploy_env" -f backstage/compose.yaml up -d --wait backstage-postgres backstage
        '''
      }
    }
  }
}
