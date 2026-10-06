pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a validar.')
    string(name: 'SERVICE', defaultValue: '', description: 'Microserviço. Em jobs nomeados, o sufixo do job é usado quando vazio.')
    string(name: 'GIT_CREDENTIAL_ID', defaultValue: '', description: 'Credential Git opcional para repositório privado.')
    string(name: 'REGISTRY', defaultValue: '', description: 'Registry sem barra final. Vazio: não publica.')
    string(name: 'REGISTRY_CREDENTIAL_ID', defaultValue: 'docker-registry', description: 'Credential Jenkins do registry.')
    booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Publicar a imagem validada.')
  }

  options { timestamps(); disableConcurrentBuilds(); skipDefaultCheckout(); timeout(time: 30, unit: 'MINUTES'); buildDiscarder(logRotator(numToKeepStr: '20')) }

  stages {
    stage('Checkout') {
      steps {
        script {
          env.SELECTED_BRANCH = params.BRANCH.trim()
          sh 'git check-ref-format --branch "$SELECTED_BRANCH"'
          def remote = [url: 'https://github.com/jvteodoro/BookRush.git']
          if (params.GIT_CREDENTIAL_ID?.trim()) { remote.credentialsId = params.GIT_CREDENTIAL_ID.trim() }
          deleteDir()
          def revision = checkout([$class: 'GitSCM', branches: [[name: "refs/remotes/origin/${env.SELECTED_BRANCH}"]], userRemoteConfigs: [remote]])
          env.GIT_COMMIT = revision.GIT_COMMIT
          env.SELECTED_SERVICE = params.SERVICE?.trim()
          if (!env.SELECTED_SERVICE) {
            env.SELECTED_SERVICE = env.JOB_NAME.replaceFirst(/^.*bookrush-microservice-/, '')
          }
          currentBuild.description = "${env.SELECTED_SERVICE} — ${env.SELECTED_BRANCH} @ ${env.GIT_COMMIT.take(7)}"
        }
      }
    }
    stage('Change gate e contratos') {
      steps {
        sh 'bash scripts/validate-change.sh --ci'
        sh 'bash scripts/test-microservice.sh "$SELECTED_SERVICE"'
      }
    }
    stage('Publicar') {
      when { expression { return params.PUBLISH && params.REGISTRY?.trim() } }
      steps {
        script {
          env.SERVICE_IMAGE = "${params.REGISTRY.trim().replaceAll('/$', '')}/bookrush/${env.SELECTED_SERVICE}:${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
          sh 'docker tag "bookrush/$SELECTED_SERVICE:$BUILD_NUMBER" "$SERVICE_IMAGE"'
        }
        withCredentials([usernamePassword(credentialsId: params.REGISTRY_CREDENTIAL_ID, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh 'echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin && docker push "$SERVICE_IMAGE"'
        }
      }
    }
  }
}
