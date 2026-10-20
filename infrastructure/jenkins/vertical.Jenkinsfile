pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a validar.')
    string(name: 'VERTICAL', defaultValue: '', description: 'Vertical. Em jobs nomeados, o sufixo do job é usado quando vazio.')
    string(name: 'GIT_CREDENTIAL_ID', defaultValue: '', description: 'Credential Git opcional para repositório privado.')
    string(name: 'REGISTRY', defaultValue: '', description: 'Registry sem barra final. Obrigatório para publicar ou Kubernetes.')
    string(name: 'REGISTRY_CREDENTIAL_ID', defaultValue: 'docker-registry', description: 'Credential Jenkins do registry.')
    booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Publicar todas as imagens validadas da vertical.')
    choice(name: 'DEPLOY_TARGET', choices: ['none', 'compose', 'kubernetes'], description: 'Destino do deploy; none apenas valida.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Implantar todas as imagens validadas da vertical.')
    string(name: 'KUBECONFIG_CREDENTIAL_ID', defaultValue: 'bookrush-kubeconfig', description: 'Credential Jenkins do tipo Secret file para Kubernetes.')
  }

  options { timestamps(); disableConcurrentBuilds(); skipDefaultCheckout(); timeout(time: 60, unit: 'MINUTES'); buildDiscarder(logRotator(numToKeepStr: '20')) }

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
          env.SELECTED_VERTICAL = params.VERTICAL?.trim()
          if (!env.SELECTED_VERTICAL) {
            env.SELECTED_VERTICAL = env.JOB_NAME.replaceFirst(/^.*bookrush-vertical-/, '')
          }
          currentBuild.description = "${env.SELECTED_VERTICAL} — ${env.SELECTED_BRANCH} @ ${env.GIT_COMMIT.take(7)}"
          if (params.PUBLISH && !params.REGISTRY?.trim()) { error('PUBLISH exige REGISTRY.') }
          if (params.DEPLOY && params.DEPLOY_TARGET == 'none') { error('DEPLOY exige DEPLOY_TARGET.') }
          if (params.DEPLOY && params.DEPLOY_TARGET == 'kubernetes' && !params.REGISTRY?.trim()) { error('Deploy Kubernetes exige REGISTRY.') }
          if (params.DEPLOY && params.DEPLOY_TARGET == 'kubernetes' && !params.PUBLISH) { error('Deploy Kubernetes exige PUBLISH para disponibilizar as imagens no registry.') }
        }
      }
    }
    stage('Change gate e testes da vertical') {
      steps {
        sh 'bash scripts/validate-change.sh --ci'
        sh 'bash scripts/test-vertical.sh "$SELECTED_VERTICAL"'
      }
    }
    stage('Publicar imagens') {
      when { expression { return params.PUBLISH && params.REGISTRY?.trim() } }
      steps {
        withCredentials([usernamePassword(credentialsId: params.REGISTRY_CREDENTIAL_ID, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh '''
            set -euo pipefail
            registry="${REGISTRY%/}"
            tag="${BUILD_NUMBER}-$(printf '%s' "$GIT_COMMIT" | cut -c1-7)"
            echo "$REGISTRY_PASSWORD" | docker login "$registry" -u "$REGISTRY_USER" --password-stdin
            services=$(bash scripts/vertical-services.sh "$SELECTED_VERTICAL")
            printf '%s\n' "$services" | while read -r service; do
              image="$registry/bookrush/$service:$tag"
              docker tag "bookrush/$service:$BUILD_NUMBER" "$image"
              docker push "$image"
            done
          '''
        }
      }
    }
    stage('Deploy Compose') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'compose' } }
      steps {
        sh '''
          set -euo pipefail
          services=$(bash scripts/vertical-services.sh "$SELECTED_VERTICAL")
          printf '%s\n' "$services" | while read -r service; do
            bash scripts/deploy-compose-service.sh "$service" "bookrush/$service:$BUILD_NUMBER"
          done
        '''
      }
    }
    stage('Deploy Kubernetes') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'kubernetes' } }
      steps {
        withCredentials([file(credentialsId: params.KUBECONFIG_CREDENTIAL_ID, variable: 'KUBECONFIG')]) {
          sh '''
            set -euo pipefail
            registry="${REGISTRY%/}"
            tag="${BUILD_NUMBER}-$(printf '%s' "$GIT_COMMIT" | cut -c1-7)"
            services=$(bash scripts/vertical-services.sh "$SELECTED_VERTICAL")
            printf '%s\n' "$services" | while read -r service; do
              bash scripts/deploy-kubernetes-service.sh "$service" "$registry/bookrush/$service:$tag"
            done
          '''
        }
      }
    }
  }
}
