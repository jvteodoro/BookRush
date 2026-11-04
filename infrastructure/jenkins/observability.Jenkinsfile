pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch da stack de observabilidade.')
    string(name: 'GRAFANA_ADMIN_CREDENTIAL_ID', defaultValue: 'observability-grafana-admin-password', description: 'Jenkins Secret text da senha local de recuperação do Grafana.')
    string(name: 'POSTGRES_EXPORTER_CREDENTIAL_ID', defaultValue: 'observability-postgres-exporter-dsn', description: 'Jenkins Secret text com DSN read-only do postgres-exporter.')
    string(name: 'GRAFANA_OIDC_CREDENTIAL_ID', defaultValue: 'grafana-oidc-client-secret', description: 'Jenkins Secret text do client confidencial Grafana no Keycloak.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Implantar a stack no Compose de produção.')
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    skipDefaultCheckout()
    timeout(time: 30, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  stages {
    stage('Checkout') {
      steps {
        script {
          env.SELECTED_BRANCH = params.BRANCH.trim()
          sh 'git check-ref-format --branch "$SELECTED_BRANCH"'
          deleteDir()
          def revision = checkout([$class: 'GitSCM',
            branches: [[name: "refs/remotes/origin/${env.SELECTED_BRANCH}"]],
            userRemoteConfigs: [[url: 'https://github.com/jvteodoro/BookRush.git']]])
          env.GIT_COMMIT = revision.GIT_COMMIT
          currentBuild.description = "${env.SELECTED_BRANCH} @ ${env.GIT_COMMIT.take(7)}"
        }
      }
    }

    stage('Validar') {
      steps {
        sh 'bash scripts/validate-observability-compose.sh'
        sh 'bash -n infrastructure/jenkins/deploy-observability.sh scripts/test-observability-stack.sh'
      }
    }

    stage('Deploy Compose') {
      when { expression { return params.DEPLOY } }
      steps {
        withCredentials([
          string(credentialsId: params.GRAFANA_ADMIN_CREDENTIAL_ID, variable: 'OBS_GRAFANA_ADMIN_PASSWORD'),
          string(credentialsId: params.POSTGRES_EXPORTER_CREDENTIAL_ID, variable: 'OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME'),
          string(credentialsId: params.GRAFANA_OIDC_CREDENTIAL_ID, variable: 'OBS_GRAFANA_OIDC_CLIENT_SECRET')
        ]) {
          sh '''#!/usr/bin/env bash
            set -Eeuo pipefail
            set +x
            umask 077
            env_file="$(mktemp /tmp/bookrush-observability-env.XXXXXX)"
            trap 'rm -f "$env_file"' EXIT
            printf '%s\\n' \\
              "OBS_GRAFANA_ADMIN_PASSWORD=$OBS_GRAFANA_ADMIN_PASSWORD" \\
              "OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME=$OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME" \\
              "OBS_GRAFANA_OIDC_ENABLED=true" \\
              "OBS_GRAFANA_OIDC_CLIENT_ID=bookrush-grafana" \\
              "OBS_GRAFANA_OIDC_CLIENT_SECRET=$OBS_GRAFANA_OIDC_CLIENT_SECRET" \\
              "OBS_GRAFANA_ROOT_URL=https://grafana-bookrush.jteodoro.tec.br/" \\
              "OBS_CONFIG_VOLUME=bookrush-observability-config" \\
              "BOOKRUSH_NETWORK_NAME=bookrush_bookrush" > "$env_file"
            OBSERVABILITY_ENV_FILE="$env_file" bash infrastructure/jenkins/deploy-observability.sh
          '''
        }
      }
    }
  }
}
