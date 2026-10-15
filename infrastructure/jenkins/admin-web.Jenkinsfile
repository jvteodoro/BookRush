pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a validar e publicar.')
    string(name: 'GIT_CREDENTIAL_ID', defaultValue: '', description: 'Credential Git opcional para repositório privado.')
    string(name: 'REGISTRY', defaultValue: '', description: 'Registry sem barra final. Vazio: não publica.')
    string(name: 'REGISTRY_CREDENTIAL_ID', defaultValue: 'docker-registry', description: 'Credential Jenkins do registry.')
    booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Publicar a imagem validada no registry.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Atualizar admin-web no Compose do host.')
  }

  options { timestamps(); disableConcurrentBuilds(); skipDefaultCheckout(); timeout(time: 20, unit: 'MINUTES'); buildDiscarder(logRotator(numToKeepStr: '20')) }

  environment {
    ADMIN_WEB_BASE_PATH = '/admin/'
    ADMIN_WEB_USE_MOCKS = 'false'
    ADMIN_WEB_KEYCLOAK_URL = 'https://keycloak-bookrush.jteodoro.tec.br'
    ADMIN_WEB_KEYCLOAK_REALM = 'bookrush-platform'
    ADMIN_WEB_KEYCLOAK_CLIENT_ID = 'labsoft-admin-web'
    ADMIN_WEB_CATALOG_API_URL = '/api'
    ADMIN_WEB_INGESTION_API_URL = '/ingestion'
    ADMIN_WEB_ANALYTICS_API_URL = '/analytics'
    ADMIN_WEB_FEED_API_URL = '/feed'
  }

  stages {
    stage('Checkout') {
      steps {
        script {
          env.SELECTED_BRANCH = (params.BRANCH ?: 'main').trim()
          sh 'git check-ref-format --branch "$SELECTED_BRANCH"'
          def remote = [url: 'https://github.com/jvteodoro/BookRush.git']
          if (params.GIT_CREDENTIAL_ID?.trim()) { remote.credentialsId = params.GIT_CREDENTIAL_ID.trim() }
          deleteDir()
          def revision = checkout([$class: 'GitSCM',
            branches: [[name: "refs/remotes/origin/${env.SELECTED_BRANCH}"]],
            userRemoteConfigs: [remote]])
          env.GIT_COMMIT = revision.GIT_COMMIT
          currentBuild.description = "${env.SELECTED_BRANCH} @ ${env.GIT_COMMIT.take(7)}"
          if (params.PUBLISH && !params.REGISTRY?.trim()) { error('PUBLISH exige REGISTRY.') }
        }
      }
    }

    stage('Build, testes e lint') {
      steps {
        sh '''docker build --target build -t "bookrush/admin-web-test:$BUILD_NUMBER" \
          --build-arg VITE_USE_MOCKS="$ADMIN_WEB_USE_MOCKS" \
          --build-arg VITE_KEYCLOAK_URL="$ADMIN_WEB_KEYCLOAK_URL" \
          --build-arg VITE_KEYCLOAK_REALM="$ADMIN_WEB_KEYCLOAK_REALM" \
          --build-arg VITE_KEYCLOAK_CLIENT_ID="$ADMIN_WEB_KEYCLOAK_CLIENT_ID" \
          --build-arg VITE_CATALOG_API_URL="$ADMIN_WEB_CATALOG_API_URL" \
          --build-arg VITE_INGESTION_API_URL="$ADMIN_WEB_INGESTION_API_URL" \
          --build-arg VITE_ANALYTICS_API_URL="$ADMIN_WEB_ANALYTICS_API_URL" \
          --build-arg VITE_FEED_API_URL="$ADMIN_WEB_FEED_API_URL" \
          --build-arg VITE_BASE_PATH="$ADMIN_WEB_BASE_PATH" admin-web'''
      }
    }

    stage('Imagem') {
      steps {
        script {
          def registry = params.REGISTRY?.trim() ? params.REGISTRY.trim().replaceAll('/$', '') : 'local'
          env.ADMIN_WEB_IMAGE = "${registry}/bookrush/admin-web:${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
        }
        sh '''docker build -t "$ADMIN_WEB_IMAGE" \
          --build-arg VITE_USE_MOCKS="$ADMIN_WEB_USE_MOCKS" \
          --build-arg VITE_KEYCLOAK_URL="$ADMIN_WEB_KEYCLOAK_URL" \
          --build-arg VITE_KEYCLOAK_REALM="$ADMIN_WEB_KEYCLOAK_REALM" \
          --build-arg VITE_KEYCLOAK_CLIENT_ID="$ADMIN_WEB_KEYCLOAK_CLIENT_ID" \
          --build-arg VITE_CATALOG_API_URL="$ADMIN_WEB_CATALOG_API_URL" \
          --build-arg VITE_INGESTION_API_URL="$ADMIN_WEB_INGESTION_API_URL" \
          --build-arg VITE_ANALYTICS_API_URL="$ADMIN_WEB_ANALYTICS_API_URL" \
          --build-arg VITE_FEED_API_URL="$ADMIN_WEB_FEED_API_URL" \
          --build-arg VITE_BASE_PATH="$ADMIN_WEB_BASE_PATH" admin-web'''
      }
    }

    stage('Publicar') {
      when { expression { return params.PUBLISH && params.REGISTRY?.trim() } }
      steps {
        withCredentials([usernamePassword(credentialsId: params.REGISTRY_CREDENTIAL_ID, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh 'echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin && docker push "$ADMIN_WEB_IMAGE"'
        }
      }
    }

    stage('Deploy opcional') {
      when { expression { return params.DEPLOY } }
      steps {
        sh 'ADMIN_WEB_IMAGE="$ADMIN_WEB_IMAGE" docker compose --project-name bookrush -f infrastructure/compose.yaml --env-file /run/bookrush.env up -d --no-build --no-deps --force-recreate --wait admin-web'
      }
    }
  }

  post { always { archiveArtifacts artifacts: 'admin-web/dist/**', allowEmptyArchive: true } }
}
