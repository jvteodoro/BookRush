pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a validar.')
    string(name: 'SERVICE', defaultValue: '', description: 'Microserviço. Em jobs nomeados, o sufixo do job é usado quando vazio.')
    string(name: 'GIT_CREDENTIAL_ID', defaultValue: '', description: 'Credential Git opcional para repositório privado.')
    string(name: 'REGISTRY', defaultValue: '', description: 'Registry sem barra final. Vazio: não publica.')
    string(name: 'REGISTRY_CREDENTIAL_ID', defaultValue: 'docker-registry', description: 'Credential Jenkins do registry.')
    booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Publicar a imagem validada.')
    choice(name: 'DEPLOY_TARGET', choices: ['none', 'compose', 'kubernetes'], description: 'Destino do deploy; none apenas valida.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Implantar a imagem validada no destino escolhido.')
    booleanParam(name: 'RUN_PUBLIC_E2E', defaultValue: false, description: 'Executar o smoke autenticado do reader-bff pelo gateway público.')
    string(name: 'E2E_CREDENTIAL_ID', defaultValue: 'bookrush-product-e2e', description: 'Credential Jenkins username/password do usuário de teste no realm bookrush.')
    string(name: 'E2E_BASE_URL', defaultValue: 'https://bookrush.jteodoro.tec.br', description: 'Gateway público usado pelo smoke autenticado.')
    string(name: 'E2E_BOOK_ID', defaultValue: '', description: 'UUID de livro com publication.json aprovado para validar o content-service.')
    string(name: 'KUBECONFIG_CREDENTIAL_ID', defaultValue: 'bookrush-kubeconfig', description: 'Credential Jenkins do tipo Secret file para Kubernetes.')
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
          if (params.PUBLISH && !params.REGISTRY?.trim()) { error('PUBLISH exige REGISTRY.') }
          if (params.DEPLOY && params.DEPLOY_TARGET == 'none') { error('DEPLOY exige DEPLOY_TARGET.') }
          if (params.DEPLOY && params.DEPLOY_TARGET == 'kubernetes' && !params.REGISTRY?.trim()) { error('Deploy Kubernetes exige REGISTRY.') }
          if (params.DEPLOY && params.DEPLOY_TARGET == 'kubernetes' && !params.PUBLISH) { error('Deploy Kubernetes exige PUBLISH para disponibilizar a imagem no registry.') }
          if (params.RUN_PUBLIC_E2E && env.SELECTED_SERVICE != 'reader-bff-service') { error('RUN_PUBLIC_E2E só é válido para reader-bff-service.') }
          if (params.RUN_PUBLIC_E2E && !params.DEPLOY) { error('RUN_PUBLIC_E2E exige DEPLOY=true para testar a revisão implantada.') }
          if (params.RUN_PUBLIC_E2E && !params.E2E_BOOK_ID?.trim()) { error('RUN_PUBLIC_E2E exige E2E_BOOK_ID.') }
        }
      }
    }
    stage('Change gate e contratos') {
      steps {
        sh 'bash scripts/validate-change.sh --ci'
        sh 'bash scripts/test-microservice.sh "$SELECTED_SERVICE"'
      }
    }
    stage('Preparar imagem') {
      steps {
        script { env.SERVICE_IMAGE = "bookrush/${env.SELECTED_SERVICE}:${env.BUILD_NUMBER}" }
      }
    }
    stage('Publicar') {
      when { expression { return params.PUBLISH && params.REGISTRY?.trim() } }
      steps {
        script {
          env.PUBLISHED_IMAGE = "${params.REGISTRY.trim().replaceAll('/$', '')}/bookrush/${env.SELECTED_SERVICE}:${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
          sh 'docker tag "$SERVICE_IMAGE" "$PUBLISHED_IMAGE"'
        }
        withCredentials([usernamePassword(credentialsId: params.REGISTRY_CREDENTIAL_ID, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
          sh 'echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin && docker push "$PUBLISHED_IMAGE"'
        }
      }
    }
    stage('Deploy Compose') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'compose' } }
      steps { sh 'bash scripts/deploy-compose-service.sh "$SELECTED_SERVICE" "$SERVICE_IMAGE"' }
    }
    stage('Deploy Kubernetes') {
      when { expression { return params.DEPLOY && params.DEPLOY_TARGET == 'kubernetes' } }
      steps {
        withCredentials([file(credentialsId: params.KUBECONFIG_CREDENTIAL_ID, variable: 'KUBECONFIG')]) {
          sh 'bash scripts/deploy-kubernetes-service.sh "$SELECTED_SERVICE" "$PUBLISHED_IMAGE"'
        }
      }
    }
    stage('Smoke público autenticado') {
      when { expression { return params.RUN_PUBLIC_E2E && params.DEPLOY && params.DEPLOY_TARGET == 'compose' } }
      steps {
        withCredentials([usernamePassword(credentialsId: params.E2E_CREDENTIAL_ID, usernameVariable: 'E2E_OIDC_USERNAME', passwordVariable: 'E2E_OIDC_PASSWORD')]) {
          sh '''#!/usr/bin/env bash
            set -euo pipefail
            E2E_BASE_URL="$E2E_BASE_URL" E2E_BOOK_ID="$E2E_BOOK_ID" \
              E2E_OIDC_USERNAME="$E2E_OIDC_USERNAME" E2E_OIDC_PASSWORD="$E2E_OIDC_PASSWORD" \
              bash scripts/test-reader-bff-e2e.sh
          '''
        }
      }
    }
  }
}
