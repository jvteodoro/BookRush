pipeline {
  agent any

  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a validar.')
    string(name: 'VERTICAL', defaultValue: '', description: 'Vertical. Em jobs nomeados, o sufixo do job é usado quando vazio.')
    string(name: 'GIT_CREDENTIAL_ID', defaultValue: '', description: 'Credential Git opcional para repositório privado.')
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
        }
      }
    }
    stage('Change gate e testes da vertical') {
      steps {
        sh 'bash scripts/validate-change.sh --ci'
        sh 'bash scripts/test-vertical.sh "$SELECTED_VERTICAL"'
      }
    }
  }
}
