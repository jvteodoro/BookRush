pipeline {
  agent any

  parameters {
    string(name: 'REGISTRY', defaultValue: '', description: 'Registry sem barra final. Vazio: não publica imagens.')
    string(name: 'REGISTRY_CREDENTIAL_ID', defaultValue: 'docker-registry', description: 'Credential Jenkins de usuário/senha do registry.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Aplicar manifests no cluster após publicar imagens.')
    string(name: 'KUBECONFIG_CREDENTIAL_ID', defaultValue: 'bookrush-kubeconfig', description: 'Credential Jenkins do tipo Secret file.')
  }

  options { timestamps(); disableConcurrentBuilds() }

  stages {
    stage('Testes e build') {
      parallel {
        stage('Backend') {
          steps { dir('services/catalog-service') { sh 'mvn -B verify' } }
        }
        stage('Frontend') {
          steps { dir('frontend') { sh 'npm install && npm run build' } }
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
        sh 'docker build -t "$BACKEND_IMAGE" services/catalog-service'
        sh 'docker build -t "$FRONTEND_IMAGE" frontend'
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

    stage('Deploy Kubernetes') {
      when { expression { return params.DEPLOY && params.REGISTRY?.trim() } }
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
