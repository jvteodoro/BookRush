pipeline {
  agent any
  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a validar.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Reconstruir e iniciar admin-web no Compose do host.')
  }
  options { timestamps(); disableConcurrentBuilds(); timeout(time: 20, unit: 'MINUTES') }
  stages {
    stage('Checkout') {
      steps {
        script {
          def selectedBranch = (params.BRANCH ?: 'main').trim()
          sh 'git check-ref-format --branch "' + selectedBranch + '"'
          deleteDir()
          checkout([$class: 'GitSCM',
            branches: [[name: "refs/remotes/origin/${selectedBranch}"]],
            userRemoteConfigs: [[url: 'https://github.com/jvteodoro/BookRush.git']]])
        }
      }
    }
    stage('Build e lint') {
      steps {
        sh 'docker build --target build -t "bookrush/admin-web-test:$BUILD_NUMBER" admin-web'
      }
    }
    stage('Imagem') { steps { sh 'image="${ADMIN_WEB_IMAGE:-local/bookrush/admin-web:${BUILD_NUMBER}-${GIT_COMMIT}}"; docker build -t "$image" admin-web' } }
    stage('Deploy opcional') {
      when { expression { params.DEPLOY } }
      steps {
        sh 'docker compose --project-name bookrush -f infrastructure/compose.yaml --env-file /run/bookrush.env up -d --no-deps --build --wait admin-web'
      }
    }
  }
  post { always { archiveArtifacts artifacts: 'admin-web/dist/**', allowEmptyArchive: true } }
}
