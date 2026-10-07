pipeline {
  agent any
  parameters {
    string(name: 'BRANCH', defaultValue: 'main', description: 'Branch a validar.')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Reconstruir e iniciar admin-web no Compose do host.')
  }
  options { timestamps(); disableConcurrentBuilds(); timeout(time: 20, unit: 'MINUTES') }
  stages {
    stage('Checkout') { steps { checkout scm } }
    stage('Build e lint') { steps { dir('admin-web') { sh 'npm ci'; sh 'npm run build'; sh 'npm run lint' } } }
    stage('Imagem') { steps { sh 'image="${ADMIN_WEB_IMAGE:-local/bookrush/admin-web:${BUILD_NUMBER}-${GIT_COMMIT:0:7}}"; docker build -t "$image" admin-web' } }
    stage('Deploy opcional') {
      when { expression { params.DEPLOY } }
      steps {
        sh 'docker compose --project-name bookrush -f infrastructure/compose.yaml --env-file /run/bookrush.env up -d --no-deps --build --wait admin-web'
      }
    }
  }
  post { always { archiveArtifacts artifacts: 'admin-web/dist/**', allowEmptyArchive: true } }
}
