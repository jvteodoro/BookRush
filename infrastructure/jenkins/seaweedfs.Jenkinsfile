pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a validar.')
    string(name: 'GIT_CREDENTIAL_ID', defaultValue: '', description: 'Credential Git opcional para repositório privado.')
    string(name: 'REGISTRY', defaultValue: '', description: 'Registry sem barra final. Vazio: não publica.')
    string(name: 'REGISTRY_CREDENTIAL_ID', defaultValue: 'docker-registry', description: 'Credential Jenkins do registry.')
    booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Publicar a imagem validada.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Implantar SeaweedFS e seu proxy OIDC no Compose.')
  }

  options { timestamps(); disableConcurrentBuilds(); skipDefaultCheckout(); timeout(time: 35, unit: 'MINUTES'); buildDiscarder(logRotator(numToKeepStr: '20')) }

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
          currentBuild.description = "SeaweedFS — ${env.SELECTED_BRANCH} @ ${env.GIT_COMMIT.take(7)}"
          if (params.PUBLISH && !params.REGISTRY?.trim()) { error('PUBLISH exige REGISTRY.') }
        }
      }
    }
    stage('Change gate e contratos') {
      steps {
        sh 'bash scripts/validate-change.sh --ci'
        sh 'bash scripts/validate-pipelines.sh'
        sh 'bash -n infrastructure/seaweedfs/start.sh'
        sh 'bash scripts/test-storage.sh'
      }
    }
    stage('Build da imagem') {
      steps {
        script { env.SEAWEEDFS_IMAGE = "bookrush/seaweedfs:${env.BUILD_NUMBER}" }
        sh 'docker build --file infrastructure/seaweedfs/Dockerfile --tag "$SEAWEEDFS_IMAGE" infrastructure/seaweedfs'
      }
    }
    stage('Publicar') {
      when { expression { return params.PUBLISH && params.REGISTRY?.trim() } }
      steps {
        script { env.PUBLISHED_IMAGE = "${params.REGISTRY.trim().replaceAll('/$', '')}/bookrush/seaweedfs:${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}" }
        withCredentials([usernamePassword(credentialsId: params.REGISTRY_CREDENTIAL_ID, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh 'docker tag "$SEAWEEDFS_IMAGE" "$PUBLISHED_IMAGE"'
          sh 'echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin && docker push "$PUBLISHED_IMAGE"'
        }
      }
    }
    stage('Deploy Compose') {
      when { expression { return params.DEPLOY } }
      steps { sh 'bash infrastructure/jenkins/deploy-seaweedfs.sh "$SEAWEEDFS_IMAGE"' }
    }
  }
}
