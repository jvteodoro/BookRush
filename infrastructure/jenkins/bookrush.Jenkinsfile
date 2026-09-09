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

    stage('Imagens') {
      steps {
        script {
          def registry = params.REGISTRY?.trim() ? params.REGISTRY.trim().replaceAll('/$', '') : 'local'
          env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
          env.BACKEND_IMAGE = "${registry}/bookrush/catalog-service:${env.IMAGE_TAG}"
          env.FRONTEND_IMAGE = "${registry}/bookrush/frontend:${env.IMAGE_TAG}"
        }
        sh 'docker build --target build -t "bookrush/catalog-test:$BUILD_NUMBER" services/catalog-service'
        sh 'docker run --rm "bookrush/catalog-test:$BUILD_NUMBER" mvn -B verify'
        sh 'docker build -t "$BACKEND_IMAGE" services/catalog-service'
        sh 'docker build -t "$FRONTEND_IMAGE" frontend'
      }
    }

    stage('Integração PostgreSQL e S3') {
      steps {
        sh 'bash scripts/test-storage.sh'
      }
    }

    stage('Publicar') {
      when { expression { return params.REGISTRY?.trim() } }
      steps {
        withCredentials([usernamePassword(credentialsId: params.REGISTRY_CREDENTIAL_ID, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh 'echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin'
          sh 'docker push "$BACKEND_IMAGE" && docker push "$FRONTEND_IMAGE"'
        }
      }
    }

    stage('Deploy Compose') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'compose' } }
      steps {
        sh 'bash /opt/bookrush/deploy-compose.sh'
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
